package com.secondhand.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.secondhand.constant.OrderStatus;
import com.secondhand.constant.ProductStatus;
import com.secondhand.entity.BatchOperationResult;
import com.secondhand.entity.Order;
import com.secondhand.entity.OrderBatchShipItem;
import com.secondhand.entity.Product;
import com.secondhand.entity.ProductImage;
import com.secondhand.entity.User;
import com.secondhand.entity.Evaluation;
import com.secondhand.mapper.EvaluationMapper;
import com.secondhand.mapper.OrderMapper;
import com.secondhand.mapper.ProductImageMapper;
import com.secondhand.mapper.ProductMapper;
import com.secondhand.mapper.UserMapper;
import com.secondhand.service.NotificationService;
import com.secondhand.service.OrderService;
import com.secondhand.util.Result;
import com.secondhand.websocket.ProductChangePublisher;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单服务实现类
 */
@Service  // 标识为Service层组件
public class OrderServiceImpl implements OrderService {

    // 日志记录器
    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    @Autowired  // 注入订单Mapper
    private OrderMapper orderMapper;
    @Autowired  // 注入商品Mapper
    private ProductMapper productMapper;
    @Autowired  // 注入商品图片Mapper
    private ProductImageMapper productImageMapper;
    @Autowired  // 注入用户Mapper
    private UserMapper userMapper;
    @Autowired
    private EvaluationMapper evaluationMapper;
    @Autowired  // 注入Redis模板
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired  // 注入Redisson客户端（分布式锁）
    private RedissonClient redissonClient;
    @Autowired  // 注入通知服务（订单状态变更时通知买卖家）
    private NotificationService notificationService;
    @Autowired
    private ProductChangePublisher productChanges;

    // 订单分布式锁的key前缀
    private static final String ORDER_LOCK_KEY = "order:lock:product:";
    // 获取锁的等待时间（秒）
    private static final long LOCK_WAIT_SECONDS = 3;
    // 锁的持有时间（秒）
    private static final long LOCK_LEASE_SECONDS = 10;

    /** 卖家提醒买家核实收货，同一订单每24小时最多提醒一次。 */
    @Override
    @Transactional
    public Result remindBuyer(Long sellerId, Long orderId) {
        // 1. 查询订单，校验卖家身份和订单状态
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getSellerId().equals(sellerId)) {
            return Result.error("订单不存在或无权提醒");
        }
        if (!OrderStatus.isWaitConfirm(order.getStatus())) {
            return Result.error("仅待买家确认的订单可以提醒；有问题时请先协商");
        }

        // 2. 校验距离上次提醒是否已满24小时
        Date now = new Date();
        Date cutoff = new Date(now.getTime() - TimeUnit.HOURS.toMillis(24));
        if (order.getLastRemindTime() != null && order.getLastRemindTime().after(cutoff)) {
            return Result.error("24小时内已经提醒过，请勿重复催促");
        }

        // 3. 更新提醒时间，防止重复请求发送多条通知
        int changed = orderMapper.update(null, new UpdateWrapper<Order>()
                .eq("id", orderId)
                .eq("seller_id", sellerId)
                .eq("status", OrderStatus.WAIT_CONFIRM)
                .and(query -> query.isNull("last_remind_time").or().le("last_remind_time", cutoff))
                .set("last_remind_time", now));
        if (changed != 1) {
            return Result.error("订单状态已变化或已提醒，请刷新");
        }
        order.setLastRemindTime(now);

        // 4. 通知买家核实收货，返回提醒结果
        notificationService.notifyUser(order.getBuyerId(), "order", "卖家提醒确认收货",
                "请核实订单（" + order.getOrderNo() + "）是否已收到商品；未收到或有问题请在订单中反馈，请勿提前确认完成", orderId);
        return Result.success("提醒已发送，每24小时可提醒一次");
    }

    /** 买家反馈交付问题，将待确认订单转为交易待协商。 */
    @Override
    @Transactional
    public Result reportDeliveryIssue(Long buyerId, Long orderId, String issue) {
        // 1. 校验问题说明，去除首尾空格后长度为1至500字
        if (issue == null || issue.trim().isEmpty() || issue.trim().length() > 500) {
            return Result.error("请填写1至500字的问题说明");
        }

        // 2. 锁定订单，校验买家身份和订单状态
        Order order = lockOrderForTransition(orderId);
        if (order == null || !order.getBuyerId().equals(buyerId)) {
            return Result.error("订单不存在或无权反馈");
        }
        if (!OrderStatus.isWaitConfirm(order.getStatus())) {
            return Result.error("仅待确认交付的订单可反馈；其他情况请联系对方");
        }

        // 3. 保存问题说明，将订单改为交易待协商
        order.setDeliveryIssue(issue.trim()); // 买家填写的问题说明
        order.setDeliveryIssueTime(new Date());
        order.setStatus(OrderStatus.NEGOTIATING); // 交易待协商，暂停卖家的确认提醒
        orderMapper.updateById(order);

        // 4. 通知卖家处理问题，返回处理结果
        notificationService.notifyUser(order.getSellerId(), "order", "买家反馈交易问题",
                "订单（" + order.getOrderNo() + "）买家反馈：" + issue.trim() + "。已暂停确认提醒，请通过站内聊天协商", orderId);
        return Result.success("已记录问题并通知卖家，请联系对方协商");
    }

    private Order lockOrderForTransition(Long orderId) {
        return orderMapper.selectOne(new QueryWrapper<Order>()
                .eq("id", orderId).last("FOR UPDATE"));
    }

    @Override
    @Transactional
    public Result acceptOrder(Long sellerId, Long orderId) {
        // 1. 锁定订单，校验卖家身份和待接单状态
        Order order = lockOrderForTransition(orderId);
        if (order == null || !order.getSellerId().equals(sellerId)) {
            return Result.error("订单不存在或无权接单");
        }
        if (!OrderStatus.isWaitAccept(order.getStatus())) {
            return Result.error("仅待接单订单可接单");
        }

        // 2. 将订单改为待面交，记录接单时间
        Date now = new Date();
        // 仅更新仍处于待接单的本人订单，重复接单不重复通知。
        int changed = orderMapper.update(null, new UpdateWrapper<Order>()
                .eq("id", orderId).eq("seller_id", sellerId).eq("status", OrderStatus.WAIT_ACCEPT)
                .set("status", OrderStatus.WAIT_MEETUP).set("accept_time", now));
        if (changed != 1) {
            return Result.error("订单状态已变化，请刷新后重试");
        }
        order.setStatus(OrderStatus.WAIT_MEETUP);
        order.setAcceptTime(now);

        // 3. 通知买家卖家已接单，返回接单结果
        notificationService.notifyUser(order.getBuyerId(), "order", "卖家已接单",
                "卖家已接单，请通过站内聊天协商面交时间", orderId);
        return Result.success("接单成功，请联系买家协商面交时间");
    }

    @Override
    public Result getOrdersWithUser(Long userId, Long peerId, Integer page, Integer size) {
        // 1. 校验当前用户和聊天对象
        if (userId == null || userId <= 0) {
            return Result.error("未登录");
        }
        if (peerId == null || peerId <= 0 || peerId.equals(userId)) {
            return Result.error("请选择有效的聊天对象");
        }

        // 2. 设置分页参数，最多查询50条
        int current = page == null || page < 1 ? 1 : page;
        int limit = size == null || size < 1 ? 10 : Math.min(size, 50);

        // 3. 查询双方订单，排除本人隐藏的记录，进行中订单优先
        // 买家和卖家两个方向都必须同时绑定本人及对方。
        QueryWrapper<Order> query = new QueryWrapper<>();
        query.and(pair -> pair.nested(buy -> buy.eq("buyer_id", userId).eq("seller_id", peerId).eq("buyer_deleted", 0))
                .or().nested(sell -> sell.eq("seller_id", userId).eq("buyer_id", peerId).eq("seller_deleted", 0)))
                .orderByAsc("CASE WHEN status IN (1,2,5,6) THEN 0 ELSE 1 END")
                .orderByDesc("create_time", "id");

        // 4. 分页查询并补齐展示信息
        return queryOrders(current, limit, query);
    }

    @Override
    public Result getOrderDetail(Long userId, Long orderId) {
        // 1. 查询订单，校验当前用户是否为买卖双方
        Order order = lockOrderForTransition(orderId);
        if (order == null || userId == null ||
                (!userId.equals(order.getBuyerId()) && !userId.equals(order.getSellerId()))) {
            return Result.error("订单不存在或无权查看");
        }

        // 2. 补齐商品、用户和评价信息
        enrichOrders(Collections.singletonList(order));
        order.setEvaluation(evaluationMapper.selectOne(new LambdaQueryWrapper<Evaluation>()
                .eq(Evaluation::getOrderId, orderId).eq(Evaluation::getFromUserId, order.getBuyerId())));

        // 3. 返回订单详情
        return Result.success(order);
    }

    /**
     * 创建订单
     */
    @Override
    @Transactional  // 开启事务
    public Result createOrder(Long buyerId, Long productId, Integer quantity, String address) {
        // 1. 校验购买数量、面交地点和商品ID
        if (quantity == null || quantity <= 0) {
            return Result.error("购买数量必须大于 0");
        }
        // 校验面交地点
        if (address == null || address.trim().isEmpty()) {
            return Result.error("请填写面交地点");
        }
        // 校验商品ID
        if (productId == null || productId <= 0) {
            return Result.error("商品 ID 无效");
        }

        // 2. 获取商品锁，协调同一商品的下单操作
        RLock lock = redissonClient.getLock(ORDER_LOCK_KEY + productId);
        boolean locked = false;
        try {
            // 尝试获取锁
            locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                return Result.error("下单的人有点多，请稍后再试");
            }

            // 3. 持有锁时执行下单逻辑
            return doCreateOrder(buyerId, productId, quantity, address);
        } catch (InterruptedException ex) {
            // 中断异常，恢复中断状态
            Thread.currentThread().interrupt();
            return Result.error("系统繁忙，请重试");
        } finally {
            // 4. 最后释放当前线程持有的锁
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 执行下单逻辑（持有锁时调用）
     */
    private Result doCreateOrder(Long buyerId, Long productId, Integer quantity, String address) {
        // 1. 锁定商品并读取最新库存，行锁一直保留到事务结束
        Product product = productMapper.selectByIdForUpdate(productId);
        // 商品不存在
        if (product == null) {
            return Result.error("商品不存在");
        }
        // 商品状态不是1（在售）
        if (!ProductStatus.isOnSale(product.getStatus())) {
            return Result.error("商品已下架或已售罄");
        }
        // 不能购买自己的商品
        if (product.getSellerId().equals(buyerId)) {
            return Result.error("不能购买自己发布的商品");
        }
        // 库存不足
        if (product.getStock() == null || product.getStock() < quantity) {
            return Result.error("库存不足");
        }

        // 2. 核对买家确认的面交地点
        // 以商品地点为准，避免买家按旧地点下单。
        String location = product.getTradeLocation();
        if (location == null || location.trim().isEmpty()) {
            return Result.error("卖家尚未设置面交地点，请联系卖家补充后再下单");
        }
        location = location.trim();
        if (!location.equals(address.trim())) {
            return Result.error("商品面交地点已变更，请刷新页面后重新确认");
        }

        // 3. 检查买家是否已有该商品的未完成订单
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getBuyerId, buyerId)
                .eq(Order::getProductId, productId)
                .in(Order::getStatus, 1, 2, 5, 6);  // 明确列出未完成状态，包含待接单
        if (orderMapper.selectCount(wrapper) > 0) {
            return Result.error("您已有该商品的未完成订单");
        }

        // 4. 生成订单号，计算总金额
        // 订单号：时间戳 + 6位随机数
        String orderNo = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
                + String.format("%06d", new Random().nextInt(999999));
        // 计算商品总金额
        BigDecimal productAmount = product.getPrice().multiply(BigDecimal.valueOf(quantity.longValue()));

        // 5. 保存订单和商品快照
        Order order = new Order();
        order.setOrderNo(orderNo);  // 订单号
        order.setBuyerId(buyerId);  // 买家ID
        order.setSellerId(product.getSellerId());  // 卖家ID
        order.setProductId(productId);  // 商品ID
        order.setSnapshotTitle(product.getTitle());
        order.setUnitPrice(product.getPrice());
        List<ProductImage> images = productImageMapper.selectByProductIds(Collections.singletonList(productId));
        order.setSnapshotImage(images.isEmpty() ? null : images.get(0).getUrl());
        order.setQuantity(quantity);  // 购买数量
        order.setProductAmount(productAmount);  // 商品金额
        order.setFreight(BigDecimal.ZERO);  // 运费（默认0）
        order.setTotalAmount(productAmount);  // 总金额
        order.setAddress(location);  // 下单时的地点快照，商品修改不影响已有订单
        order.setStatus(OrderStatus.WAIT_ACCEPT);  // 新订单先等待卖家接单
        order.setCreateTime(new Date());  // 创建时间
        orderMapper.insert(order);  // 保存订单

        // 6. 扣减库存，更新商品状态
        product.setStock(product.getStock() - quantity);
        // 库存为0时，商品状态改为3（已售罄）
        product.setStatus(product.getStock() == 0 ? ProductStatus.SOLD_OUT : ProductStatus.ON_SALE);
        product.setUpdateTime(new Date());  // 更新时间
        productMapper.updateById(product);  // 更新商品

        // 7. 删除商品详情缓存，发布商品变更事件
        redisTemplate.delete("product:detail:" + productId);
        if (productChanges != null) productChanges.publish(productId);

        // 记录日志
        log.info("订单创建成功：订单号={} 买家ID={} 商品ID={} 数量={}",
                orderNo, buyerId, productId, quantity);

        // 8. 通知卖家接单，返回下单结果
        notificationService.notifyUser(product.getSellerId(), "order", "新订单",
                "您有一笔新订单「" + product.getTitle() + "」，请及时接单并协商面交时间", order.getId());
        return Result.success("下单成功");
    }

    /**
     * 查询我买到的订单
     */
    @Override
    public Result getMyBoughtOrders(Long buyerId, Integer page, Integer size) {
        // 1. 设置分页参数
        if (page == null || page < 1) {
            page = 1;
        }
        // 每页条数默认12
        if (size == null || size < 1) {
            size = 12;
        }

        // 2. 查询本人买家视角的订单，排除本人隐藏的记录
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getBuyerId, buyerId)
                .eq(Order::getBuyerDeleted, 0)
                .orderByDesc(Order::getCreateTime);

        // 3. 分页查询并补齐展示信息
        return queryOrders(page, size, wrapper);
    }

    /**
     * 查询我卖出的订单
     */
    @Override
    public Result getMySoldOrders(Long sellerId, Integer page, Integer size) {
        // 1. 设置分页参数
        if (page == null || page < 1) {
            page = 1;
        }
        // 每页条数默认12
        if (size == null || size < 1) {
            size = 12;
        }

        // 2. 查询本人卖家视角的订单，排除本人隐藏的记录
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getSellerId, sellerId)
                .eq(Order::getSellerDeleted, 0)
                .orderByDesc(Order::getCreateTime);

        // 3. 分页查询并补齐展示信息
        return queryOrders(page, size, wrapper);
    }

    /**
     * 买家隐藏单条订单
     */
    @Override
    @Transactional
    public Result hideBoughtOrder(Long buyerId, Long orderId) {
        // 1. 隐藏买家视角的订单记录
        String error = hideBoughtOrderInternal(buyerId, orderId);

        // 2. 返回处理结果
        if (error != null) {
            return Result.error(error);
        }
        return Result.success("已从买家订单记录中删除");
    }

    /**
     * 卖家隐藏单条订单
     */
    @Override
    @Transactional
    public Result hideSoldOrder(Long sellerId, Long orderId) {
        // 1. 隐藏卖家视角的订单记录
        String error = hideSoldOrderInternal(sellerId, orderId);

        // 2. 返回处理结果
        if (error != null) {
            return Result.error(error);
        }
        return Result.success("已从卖家订单记录中删除");
    }

    /**
     * 买家批量隐藏订单
     */
    @Override
    @Transactional
    public Result batchHideBoughtOrders(Long buyerId, List<Long> orderIds) {
        // 1. 创建批量处理结果
        BatchOperationResult result = new BatchOperationResult();

        // 2. 过滤无效ID并去重，逐笔隐藏买家订单记录
        for (Long orderId : normalizeOrderIds(orderIds)) {
            String error = hideBoughtOrderInternal(buyerId, orderId);
            if (error == null) {
                result.addSuccess(orderId);  // 成功
            } else {
                result.addFailure(orderId, error);  // 失败
            }
        }

        // 3. 返回成功订单和失败原因
        return Result.success(result);
    }

    /**
     * 卖家批量隐藏订单
     */
    @Override
    @Transactional
    public Result batchHideSoldOrders(Long sellerId, List<Long> orderIds) {
        // 1. 创建批量处理结果
        BatchOperationResult result = new BatchOperationResult();

        // 2. 过滤无效ID并去重，逐笔隐藏卖家订单记录
        for (Long orderId : normalizeOrderIds(orderIds)) {
            String error = hideSoldOrderInternal(sellerId, orderId);
            if (error == null) {
                result.addSuccess(orderId);  // 成功
            } else {
                result.addFailure(orderId, error);  // 失败
            }
        }

        // 3. 返回成功订单和失败原因
        return Result.success(result);
    }

    /**
     * 取消订单
     */
    @Override
    @Transactional
    public Result cancelOrder(Long userId, Long orderId) {
        // 1. 锁定订单，校验买家身份和可取消状态
        Order order = lockOrderForTransition(orderId);
        // 订单不存在
        if (order == null) {
            return Result.error("订单不存在");
        }
        // 当前用户不是买家
        if (!order.getBuyerId().equals(userId)) {
            return Result.error("无权操作");
        }
        // 实际交付前，买家可以取消。
        if (!OrderStatus.isWaitMeetup(order.getStatus()) && !OrderStatus.isWaitAccept(order.getStatus())) {
            return Result.error("仅待接单或待面交订单可取消");
        }

        // 2. 将订单改为已取消，记录取消时间和原因
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelTime(new Date());
        order.setCancelType(1);  // 取消类型：1-买家取消
        order.setCancelReason("买家取消订单");
        orderMapper.updateById(order);

        // 3. 回补商品库存
        restoreProductStock(order);

        log.info("订单已取消：订单号={}", order.getOrderNo());

        // 4. 通知卖家订单已取消，返回取消结果
        notificationService.notifyUser(order.getSellerId(), "order", "订单取消",
                "买家取消了订单（" + order.getOrderNo() + "）", order.getId());
        return Result.success("订单已取消");
    }

    /**
     * 订单交付
     */
    @Override
    @Transactional
    public Result shipOrder(Long sellerId, Long orderId, String expressNo) {
        // 1. 校验订单并记录面交交付
        String error = shipOrderInternal(sellerId, orderId, expressNo);

        // 2. 返回处理结果
        if (error != null) {
            return Result.error(error);
        }
        return Result.success("交付成功");
    }

    /**
     * 批量交付
     */
    @Override
    @Transactional
    public Result batchShipOrders(Long sellerId, List<OrderBatchShipItem> items) {
        // 1. 创建批量处理结果，校验是否选择订单
        BatchOperationResult result = new BatchOperationResult();
        // 列表为空
        if (items == null || items.isEmpty()) {
            result.addFailure(null, "请至少选择一条订单");
            return Result.success(result);
        }

        // 2. 逐笔校验并记录面交交付，收集成功订单和失败原因
        for (OrderBatchShipItem item : items) {
            Long orderId = item == null ? null : item.getOrderId();
            String expressNo = item == null ? null : item.getExpressNo();
            String error = shipOrderInternal(sellerId, orderId, expressNo);
            if (error == null) {
                result.addSuccess(orderId);  // 成功
            } else {
                result.addFailure(orderId, error);  // 失败
            }
        }

        // 3. 返回批量处理结果
        return Result.success(result);
    }

    /**
     * 确认收到
     */
    @Override
    @Transactional
    public Result confirmOrder(Long buyerId, Long orderId) {
        // 1. 锁定订单，校验买家身份和可确认状态
        Order order = lockOrderForTransition(orderId);
        // 订单不存在
        if (order == null) {
            return Result.error("订单不存在");
        }
        // 当前用户不是买家
        if (!order.getBuyerId().equals(buyerId)) {
            return Result.error("无权操作");
        }
        // 待买家确认或交易待协商时，买家可以核实后确认完成。
        if (!OrderStatus.isWaitConfirm(order.getStatus()) && !OrderStatus.isNegotiating(order.getStatus())) {
            return Result.error("仅待确认状态可确认");
        }

        // 2. 将订单改为已完成，记录完成时间
        order.setStatus(OrderStatus.COMPLETED);
        order.setFinishTime(new Date());  // 完成时间
        orderMapper.updateById(order);

        log.info("订单已确认收到：订单号={}", order.getOrderNo());

        // 3. 通知卖家交易完成，返回确认结果
        notificationService.notifyUser(order.getSellerId(), "order", "买家已确认收到",
                "订单（" + order.getOrderNo() + "）已完成，交易达成", order.getId());
        return Result.success("确认收到成功");
    }

    /**
     * 拒绝交付
     */
    @Override
    @Transactional
    public Result rejectOrder(Long sellerId, Long orderId, String reason) {
        // 1. 校验订单并拒绝接单或取消交易
        String error = rejectOrderInternal(sellerId, orderId, reason);

        // 2. 返回处理结果
        if (error != null) {
            return Result.error(error);
        }
        return Result.success("已拒绝交付");
    }

    /**
     * 批量拒绝交付
     */
    @Override
    @Transactional
    public Result batchRejectOrders(Long sellerId, List<Long> orderIds, String reason) {
        // 1. 创建批量处理结果
        BatchOperationResult result = new BatchOperationResult();

        // 2. 过滤无效ID并去重，逐笔拒绝接单或取消交易
        for (Long orderId : normalizeOrderIds(orderIds)) {
            String error = rejectOrderInternal(sellerId, orderId, reason);
            if (error == null) {
                result.addSuccess(orderId);  // 成功
            } else {
                result.addFailure(orderId, error);  // 失败
            }
        }

        // 3. 返回成功订单和失败原因
        return Result.success(result);
    }

    /**
     * 查询待处理订单数量
     */
    @Override
    public Result getPendingCount(Long userId) {
        // 1. 统计本人作为卖家的待接单数量（状态5）
        LambdaQueryWrapper<Order> acceptanceWrapper = new LambdaQueryWrapper<>();
        acceptanceWrapper.eq(Order::getSellerId, userId).eq(Order::getStatus, 5);
        long toAccept = orderMapper.selectCount(acceptanceWrapper);

        // 2. 统计本人作为卖家的待面交数量（状态1）
        LambdaQueryWrapper<Order> sellerWrapper = new LambdaQueryWrapper<>();
        sellerWrapper.eq(Order::getSellerId, userId).eq(Order::getStatus, 1);
        long toShip = orderMapper.selectCount(sellerWrapper);

        // 3. 统计本人作为买家的待确认数量（状态2）
        LambdaQueryWrapper<Order> buyerWrapper = new LambdaQueryWrapper<>();
        buyerWrapper.eq(Order::getBuyerId, userId).eq(Order::getStatus, 2);
        long toConfirm = orderMapper.selectCount(buyerWrapper);

        // 4. 组装统计结果，保留前端使用的字段名称
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("toAccept", toAccept);
        data.put("toShip", toShip);  // 待交付数量
        data.put("toConfirm", toConfirm);  // 待确认数量
        return Result.success(data);
    }

    /**
     * 分页查询订单（共用方法）
     */
    private Result queryOrders(Integer page, Integer size, Wrapper<Order> wrapper) {
        // 1. 按调用方提供的条件分页查询
        Page<Order> pageObj = new Page<>(page, size);
        IPage<Order> result = orderMapper.selectPage(pageObj, wrapper);

        // 2. 有订单时批量补齐展示字段
        if (!result.getRecords().isEmpty()) {
            enrichOrders(result.getRecords());
        }

        // 3. 返回分页结果
        return Result.success(result);
    }

    /**
     * 补齐订单展示字段（商品标题、图片、买卖家昵称）
     */
    private void enrichOrders(List<Order> orders) {
        // 1. 收集商品和买卖双方的ID，避免逐条查询
        Set<Long> productIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (Order order : orders) {
            productIds.add(order.getProductId());
            userIds.add(order.getBuyerId());
            userIds.add(order.getSellerId());
        }

        // 2. 批量查询当前商品，供没有快照的历史订单使用
        Map<Long, Product> productMap = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));

        // 3. 批量查询图片，记录每个商品的首图
        List<ProductImage> allImages = productImageMapper.selectByProductIds(new ArrayList<>(productIds));
        Map<Long, String> firstImageMap = new LinkedHashMap<>();
        for (ProductImage image : allImages) {
            // Mapper已按sort升序查询，每个商品只记录第一张；URL为空也不改用后续图片。
            if (!firstImageMap.containsKey(image.getProductId())) {
                firstImageMap.put(image.getProductId(), image.getUrl());
            }
        }

        // 4. 批量查询买卖双方的用户信息
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        // 5. 查询已完成订单的买家评价，确定已评价标记
        Map<Long, Long> completedBuyers = orders.stream()
                .filter(order -> OrderStatus.isCompleted(order.getStatus()))
                .collect(Collectors.toMap(Order::getId, Order::getBuyerId));
        Set<Long> evaluatedIds = new HashSet<>();
        if (!completedBuyers.isEmpty()) {
            LambdaQueryWrapper<Evaluation> query = new LambdaQueryWrapper<>();
            query.in(Evaluation::getOrderId, completedBuyers.keySet())
                    .select(Evaluation::getOrderId, Evaluation::getFromUserId);
            for (Evaluation evaluation : evaluationMapper.selectList(query)) {
                if (Objects.equals(completedBuyers.get(evaluation.getOrderId()), evaluation.getFromUserId())) {
                    evaluatedIds.add(evaluation.getOrderId());
                }
            }
        }

        // 6. 为订单补齐展示字段，有订单快照时优先使用快照
        for (Order order : orders) {
            order.setEvaluated(evaluatedIds.contains(order.getId()));
            Product product = productMap.get(order.getProductId());
            if (order.getSnapshotTitle() != null) {
                order.setProductTitle(order.getSnapshotTitle());
                order.setProductImage(order.getSnapshotImage());
            } else if (product != null) {
                // 无法还原旧订单的商品快照，页面应明确标注为当前参考信息。
                order.setProductTitle(product.getTitle());
                order.setProductImage(firstImageMap.get(product.getId()));
            }
            User buyer = userMap.get(order.getBuyerId());
            if (buyer != null) {
                order.setBuyerNickname(buyer.getNickname() != null ? buyer.getNickname() : buyer.getUsername());
            }
            User seller = userMap.get(order.getSellerId());
            if (seller != null) {
                order.setSellerNickname(seller.getNickname() != null ? seller.getNickname() : seller.getUsername());
            }
        }
    }

    /**
     * 判断订单是否可以隐藏
     */
    private boolean canHideOrderRecord(Order order) {
        Integer status = order.getStatus();
        // 状态3：已完成，状态4：已取消
        return OrderStatus.isCompleted(status) || Integer.valueOf(OrderStatus.CANCELLED).equals(status);
    }

    /**
     * 规范化订单ID列表（去重、过滤空值）
     */
    private List<Long> normalizeOrderIds(List<Long> orderIds) {
        // 1. 未选择订单时返回空列表
        if (orderIds == null || orderIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 过滤空值和非正数ID，并去重，保持原有顺序
        return orderIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 买家隐藏订单内部方法
     */
    private String hideBoughtOrderInternal(Long buyerId, Long orderId) {
        // 1. 查询订单，校验买家身份
        Order order = orderMapper.selectById(orderId);
        // 订单不存在
        if (order == null) {
            return "订单不存在";
        }
        // 当前用户不是买家
        if (!order.getBuyerId().equals(buyerId)) {
            return "无权操作";
        }

        // 2. 校验订单已结束，且当前视角尚未隐藏
        if (!canHideOrderRecord(order)) {
            return "仅已完成或已取消订单可删除记录";
        }
        // 已经被隐藏
        if (Integer.valueOf(1).equals(order.getBuyerDeleted())) {
            return "订单记录已删除";
        }

        // 3. 仅隐藏本人视角的记录，不影响对方查看
        order.setBuyerDeleted(1);
        orderMapper.updateById(order);
        log.info("买家隐藏订单记录：订单号={} 买家ID={}", order.getOrderNo(), buyerId);
        return null;  // 成功返回null
    }

    /**
     * 卖家隐藏订单内部方法
     */
    private String hideSoldOrderInternal(Long sellerId, Long orderId) {
        // 1. 查询订单，校验卖家身份
        Order order = orderMapper.selectById(orderId);
        // 订单不存在
        if (order == null) {
            return "订单不存在";
        }
        // 当前用户不是卖家
        if (!order.getSellerId().equals(sellerId)) {
            return "无权操作";
        }

        // 2. 校验订单已结束，且当前视角尚未隐藏
        if (!canHideOrderRecord(order)) {
            return "仅已完成或已取消订单可删除记录";
        }
        // 已经被隐藏
        if (Integer.valueOf(1).equals(order.getSellerDeleted())) {
            return "订单记录已删除";
        }

        // 3. 仅隐藏本人视角的记录，不影响对方查看
        order.setSellerDeleted(1);
        orderMapper.updateById(order);
        log.info("卖家隐藏订单记录：订单号={} 卖家ID={}", order.getOrderNo(), sellerId);
        return null;  // 成功返回null
    }

    /**
     * 交付内部方法
     */
    private String shipOrderInternal(Long sellerId, Long orderId, String expressNo) {
        // 1. 校验订单ID
        if (orderId == null || orderId <= 0) {
            return "订单 ID 无效";
        }

        // 2. 锁定订单，校验卖家身份、订单状态和面交时间
        Order order = lockOrderForTransition(orderId);
        // 订单不存在
        if (order == null) {
            return "订单不存在";
        }
        // 当前用户不是卖家
        if (!order.getSellerId().equals(sellerId)) {
            return "无权操作";
        }
        // 订单状态不是待交付
        if (!OrderStatus.isWaitMeetup(order.getStatus())) {
            return "请先接单，仅待面交订单可确认交付";
        }
        if (order.getMeetupTime() == null) {
            return "买家尚未确认面交时间，请先发送预约卡片并等待确认";
        }

        // 3. 记录卖家的交付声明，等待买家核实确认
        // 面交不生成物流凭证，也不能把卖家确认交付解释为已支付。
        order.setExpressNo(null);
        order.setStatus(OrderStatus.WAIT_CONFIRM);  // 状态改为待确认
        order.setDeliverTime(new Date());  // 交付时间
        orderMapper.updateById(order);
        log.info("卖家已确认面交交付：订单号={}", order.getOrderNo());

        // 4. 通知买家核实收货
        notificationService.notifyUser(order.getBuyerId(), "order", "卖家声明已交付",
                "订单（" + order.getOrderNo() + "）卖家声明已交付，请核实是否收到；未收到或有问题可在订单中反馈。付款由双方线下完成。", order.getId());
        return null;  // 成功返回null
    }

    /**
     * 拒绝交付内部方法
     */
    private String rejectOrderInternal(Long sellerId, Long orderId, String reason) {
        // 1. 校验订单ID
        if (orderId == null || orderId <= 0) {
            return "订单 ID 无效";
        }

        // 2. 锁定订单，校验卖家身份和可取消状态
        Order order = lockOrderForTransition(orderId);
        // 订单不存在
        if (order == null) {
            return "订单不存在";
        }
        // 当前用户不是卖家
        if (!order.getSellerId().equals(sellerId)) {
            return "无权操作";
        }
        if (!OrderStatus.isWaitMeetup(order.getStatus()) && !OrderStatus.isWaitAccept(order.getStatus())) {
            return "仅待接单或待面交订单可拒绝或取消";
        }
        boolean waitingAcceptance = OrderStatus.isWaitAccept(order.getStatus());

        // 3. 将订单改为已取消，保存卖家的取消原因
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelTime(new Date());
        order.setCancelType(2);  // 取消类型：2-卖家拒绝
        order.setCancelReason(normalizeRejectReason(reason));  // 拒绝原因
        orderMapper.updateById(order);

        // 4. 回补商品库存
        restoreProductStock(order);
        log.info("卖家拒绝交付：卖家ID={} 订单号={} 原因={}",
                sellerId, order.getOrderNo(), reason != null ? reason : "");

        // 5. 通知买家卖家已拒绝接单或取消交易
        notificationService.notifyUser(order.getBuyerId(), "order", waitingAcceptance ? "卖家拒绝接单" : "卖家取消交易",
                "您的订单（" + order.getOrderNo() + "）被卖家拒绝交付：" + order.getCancelReason(), order.getId());
        return null;  // 成功返回null
    }

    /**
     * 规范化拒绝原因
     */
    private String normalizeRejectReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            return "卖家拒绝交付";  // 默认原因
        }
        return reason.trim();
    }

    /**
     * 回补商品库存（订单取消或拒绝时调用）
     */
    private void restoreProductStock(Order order) {
        // 1. 尝试获取商品锁
        RLock lock = redissonClient.getLock(ORDER_LOCK_KEY + order.getProductId());
        boolean locked = false;
        try {
            // 尝试获取锁
            locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                // 未持有商品锁时不能读改写库存，抛出异常让取消或拒单操作一起回滚。
                log.warn("回补库存时未能获取锁（系统繁忙）：订单号={} 商品ID={}",
                        order.getOrderNo(), order.getProductId());
                throw new IllegalStateException("库存回补繁忙，请重试");
            }

            // 2. 锁定商品后读取最新库存，避免覆盖尚未提交的扣减或回补
            Product product = productMapper.selectByIdForUpdate(order.getProductId());
            if (product == null) {
                return;
            }

            // 3. 按订单数量回补库存
            int restoreQuantity = order.getQuantity() == null ? 1 : order.getQuantity();
            int currentStock = product.getStock() == null ? 0 : product.getStock();
            product.setStock(currentStock + restoreQuantity);

            // 4. 有库存且原为已售罄时恢复在售，保存商品
            if (product.getStock() > 0 && ProductStatus.isSoldOut(product.getStatus())) {
                product.setStatus(ProductStatus.ON_SALE);
            }
            product.setUpdateTime(new Date());
            productMapper.updateById(product);

            // 5. 删除商品详情缓存，发布商品变更事件
            redisTemplate.delete("product:detail:" + product.getId());
            if (productChanges != null) productChanges.publish(product.getId());
        } catch (InterruptedException ex) {
            // 中断异常，恢复中断状态
            Thread.currentThread().interrupt();
            // 订单已在当前事务中改为取消，回补失败必须抛出异常，让订单修改一起回滚。
            throw new IllegalStateException("库存回补被中断，请重试", ex);
        } finally {
            // 6. 最后释放当前线程持有的锁
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
