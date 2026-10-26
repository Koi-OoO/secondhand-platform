package com.secondhand.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private com.secondhand.websocket.ProductChangePublisher productChanges;

    // 订单分布式锁的key前缀
    private static final String ORDER_LOCK_KEY = "order:lock:product:";
    // 获取锁的等待时间（秒）
    private static final long LOCK_WAIT_SECONDS = 3;
    // 锁的持有时间（秒）
    private static final long LOCK_LEASE_SECONDS = 10;

    @Override
    @Transactional
    public Result remindBuyer(Long sellerId, Long orderId) {
        Order order=orderMapper.selectById(orderId);
        if(order==null || !order.getSellerId().equals(sellerId)) return Result.error("订单不存在或无权提醒");
        if(!Integer.valueOf(2).equals(order.getStatus())) return Result.error("仅待买家确认的订单可以提醒；有问题时请先协商");
        Date now=new Date(); Date cutoff=new Date(now.getTime()-TimeUnit.HOURS.toMillis(24));
        if(order.getLastRemindTime()!=null && order.getLastRemindTime().after(cutoff)) return Result.error("24小时内已经提醒过，请勿重复催促");
        int changed=orderMapper.update(null,new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Order>()
                .eq("id",orderId).eq("seller_id",sellerId).eq("status",2)
                .and(q->q.isNull("last_remind_time").or().le("last_remind_time",cutoff)).set("last_remind_time",now));
        if(changed!=1) return Result.error("订单状态已变化或已提醒，请刷新");
        order.setLastRemindTime(now);
        notificationService.notifyUser(order.getBuyerId(),"order","卖家提醒确认收货",
                "请核实订单（"+order.getOrderNo()+"）是否已收到商品；未收到或有问题请在订单中反馈，请勿提前确认完成",orderId);
        return Result.success("提醒已发送，每24小时可提醒一次");
    }

    @Override
    @Transactional
    public Result reportDeliveryIssue(Long buyerId,Long orderId,String issue) {
        if(issue==null || issue.trim().isEmpty() || issue.trim().length()>500) return Result.error("请填写1至500字的问题说明");
        Order order=orderMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Order>().eq("id",orderId).last("FOR UPDATE"));
        if(order==null || !order.getBuyerId().equals(buyerId)) return Result.error("订单不存在或无权反馈");
        if(!Integer.valueOf(2).equals(order.getStatus())) return Result.error("仅待确认交付的订单可反馈；其他情况请联系对方");
        order.setDeliveryIssue(issue.trim());order.setDeliveryIssueTime(new Date());order.setStatus(6);
        orderMapper.updateById(order);
        notificationService.notifyUser(order.getSellerId(),"order","买家反馈交易问题",
                "订单（"+order.getOrderNo()+"）买家反馈："+issue.trim()+"。已暂停确认提醒，请通过站内聊天协商",orderId);
        return Result.success("已记录问题并通知卖家，请联系对方协商");
    }

    private Order lockOrderForTransition(Long orderId) {
        return orderMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Order>()
                .eq("id", orderId).last("FOR UPDATE"));
    }

    @Override
    @Transactional
    public Result acceptOrder(Long sellerId, Long orderId) {
        Order order = lockOrderForTransition(orderId);
        if (order == null || !order.getSellerId().equals(sellerId)) return Result.error("订单不存在或无权接单");
        if (!Integer.valueOf(5).equals(order.getStatus())) return Result.error("仅待接单订单可接单");
        Date now = new Date();
        // 仅更新仍处于待接单的本人订单，重复接单不重复通知。
        int changed = orderMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Order>()
                .eq("id", orderId).eq("seller_id", sellerId).eq("status", 5)
                .set("status", 1).set("accept_time", now));
        if (changed != 1) return Result.error("订单状态已变化，请刷新后重试");
        order.setStatus(1);
        order.setAcceptTime(now);
        notificationService.notifyUser(order.getBuyerId(), "order", "卖家已接单",
                "卖家已接单，请通过站内聊天协商面交时间", orderId);
        return Result.success("接单成功，请联系买家协商面交时间");
    }

    @Override
    public Result getOrdersWithUser(Long userId, Long peerId, Integer page, Integer size) {
        if (userId == null || userId <= 0) return Result.error("未登录");
        if (peerId == null || peerId <= 0 || peerId.equals(userId)) return Result.error("请选择有效的聊天对象");
        int current = page == null || page < 1 ? 1 : page;
        int limit = size == null || size < 1 ? 10 : Math.min(size, 50);
        // 两个方向都必须绑定本人及对方；仅应用本人视角的隐藏标记。
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Order> query =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        query.and(pair -> pair.nested(buy -> buy.eq("buyer_id", userId).eq("seller_id", peerId).eq("buyer_deleted", 0))
                .or().nested(sell -> sell.eq("seller_id", userId).eq("buyer_id", peerId).eq("seller_deleted", 0)))
                .orderByAsc("CASE WHEN status IN (1,2,5,6) THEN 0 ELSE 1 END")
                .orderByDesc("create_time", "id");
        return queryOrders(current, limit, query);
    }

    @Override
    public Result getOrderDetail(Long userId, Long orderId) {
        Order order = lockOrderForTransition(orderId);
        if (order == null || userId == null ||
                (!userId.equals(order.getBuyerId()) && !userId.equals(order.getSellerId()))) {
            return Result.error("订单不存在或无权查看");
        }
        enrichOrders(java.util.Collections.singletonList(order));
        order.setEvaluation(evaluationMapper.selectOne(new LambdaQueryWrapper<Evaluation>()
                .eq(Evaluation::getOrderId, orderId).eq(Evaluation::getFromUserId, order.getBuyerId())));
        return Result.success(order);
    }

    /**
     * 创建订单
     */
    @Override
    @Transactional  // 开启事务
    public Result createOrder(Long buyerId, Long productId, Integer quantity, String address) {
        // 校验购买数量
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

        // 获取分布式锁（防止同一商品并发下单超卖）
        RLock lock = redissonClient.getLock(ORDER_LOCK_KEY + productId);
        boolean locked = false;
        try {
            // 尝试获取锁
            locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                return Result.error("下单的人有点多，请稍后再试");
            }
            // 执行真正的下单逻辑
            return doCreateOrder(buyerId, productId, quantity, address);
        } catch (InterruptedException ex) {
            // 中断异常，恢复中断状态
            Thread.currentThread().interrupt();
            return Result.error("系统繁忙，请重试");
        } finally {
            // 释放锁
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 执行下单逻辑（持有锁时调用）
     */
    private Result doCreateOrder(Long buyerId, Long productId, Integer quantity, String address) {
        // 查询商品
        Product product = productMapper.selectById(productId);
        // 商品不存在
        if (product == null) {
            return Result.error("商品不存在");
        }
        // 商品状态不是1（在售）
        if (product.getStatus() != 1) {
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

        // 以商品地点为准；客户端传入的地址仅用于核对买家确认的版本。
        String location = product.getTradeLocation();
        if (location == null || location.trim().isEmpty()) {
            return Result.error("卖家尚未设置面交地点，请联系卖家补充后再下单");
        }
        location = location.trim();
        if (!location.equals(address.trim())) {
            return Result.error("商品面交地点已变更，请刷新页面后重新确认");
        }

        // 检查是否有未完成的订单（同商品同买家）
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getBuyerId, buyerId)
                .eq(Order::getProductId, productId)
                .in(Order::getStatus, 1, 2, 5, 6);  // 明确列出未完成状态，包含待接单
        if (orderMapper.selectCount(wrapper) > 0) {
            return Result.error("您已有该商品的未完成订单");
        }

        // 生成订单号：时间戳 + 6位随机数
        String orderNo = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
                + String.format("%06d", new Random().nextInt(999999));
        // 计算商品总金额
        BigDecimal productAmount = product.getPrice().multiply(BigDecimal.valueOf(quantity.longValue()));

        // 创建订单对象
        Order order = new Order();
        order.setOrderNo(orderNo);  // 订单号
        order.setBuyerId(buyerId);  // 买家ID
        order.setSellerId(product.getSellerId());  // 卖家ID
        order.setProductId(productId);  // 商品ID
        order.setSnapshotTitle(product.getTitle());
        order.setUnitPrice(product.getPrice());
        List<ProductImage> images = productImageMapper.selectByProductIds(java.util.Collections.singletonList(productId));
        order.setSnapshotImage(images.isEmpty() ? null : images.get(0).getUrl());
        order.setQuantity(quantity);  // 购买数量
        order.setProductAmount(productAmount);  // 商品金额
        order.setFreight(BigDecimal.ZERO);  // 运费（默认0）
        order.setTotalAmount(productAmount);  // 总金额
        order.setAddress(location);  // 下单时的地点快照，商品修改不影响已有订单
        order.setStatus(5);  // 新订单先等待卖家接单
        order.setCreateTime(new Date());  // 创建时间
        orderMapper.insert(order);  // 保存订单

        // 扣减库存
        product.setStock(product.getStock() - quantity);
        // 库存为0时，商品状态改为3（已售罄）
        product.setStatus(product.getStock() == 0 ? 3 : 1);
        product.setUpdateTime(new Date());  // 更新时间
        productMapper.updateById(product);  // 更新商品
        // 删除商品详情缓存
        redisTemplate.delete("product:detail:" + productId);
        if (productChanges != null) productChanges.publish(productId);

        // 记录日志
        log.info("订单创建成功：订单号={} 买家ID={} 商品ID={} 数量={}",
                orderNo, buyerId, productId, quantity);

        // 通知卖家：有新订单待接单
        notificationService.notifyUser(product.getSellerId(), "order", "新订单",
                "您有一笔新订单「" + product.getTitle() + "」，请及时接单并协商面交时间", order.getId());
        return Result.success("下单成功");
    }

    /**
     * 查询我买到的订单
     */
    @Override
    public Result getMyBoughtOrders(Long buyerId, Integer page, Integer size) {
        // 页码默认1
        if (page == null || page < 1) {
            page = 1;
        }
        // 每页条数默认12
        if (size == null || size < 1) {
            size = 12;
        }

        // 构建查询条件：买家ID，买家未删除，按创建时间倒序
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getBuyerId, buyerId)
                .eq(Order::getBuyerDeleted, 0)
                .orderByDesc(Order::getCreateTime);
        return queryOrders(page, size, wrapper);
    }

    /**
     * 查询我卖出的订单
     */
    @Override
    public Result getMySoldOrders(Long sellerId, Integer page, Integer size) {
        // 页码默认1
        if (page == null || page < 1) {
            page = 1;
        }
        // 每页条数默认12
        if (size == null || size < 1) {
            size = 12;
        }

        // 构建查询条件：卖家ID，卖家未删除，按创建时间倒序
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getSellerId, sellerId)
                .eq(Order::getSellerDeleted, 0)
                .orderByDesc(Order::getCreateTime);
        return queryOrders(page, size, wrapper);
    }

    /**
     * 买家隐藏单条订单
     */
    @Override
    @Transactional
    public Result hideBoughtOrder(Long buyerId, Long orderId) {
        // 调用内部方法执行隐藏
        String error = hideBoughtOrderInternal(buyerId, orderId);
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
        // 调用内部方法执行隐藏
        String error = hideSoldOrderInternal(sellerId, orderId);
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
        // 创建批量操作结果对象
        BatchOperationResult result = new BatchOperationResult();
        // 遍历订单ID列表
        for (Long orderId : normalizeOrderIds(orderIds)) {
            String error = hideBoughtOrderInternal(buyerId, orderId);
            if (error == null) {
                result.addSuccess(orderId);  // 成功
            } else {
                result.addFailure(orderId, error);  // 失败
            }
        }
        return Result.success(result);
    }

    /**
     * 卖家批量隐藏订单
     */
    @Override
    @Transactional
    public Result batchHideSoldOrders(Long sellerId, List<Long> orderIds) {
        // 创建批量操作结果对象
        BatchOperationResult result = new BatchOperationResult();
        // 遍历订单ID列表
        for (Long orderId : normalizeOrderIds(orderIds)) {
            String error = hideSoldOrderInternal(sellerId, orderId);
            if (error == null) {
                result.addSuccess(orderId);  // 成功
            } else {
                result.addFailure(orderId, error);  // 失败
            }
        }
        return Result.success(result);
    }

    /**
     * 取消订单
     */
    @Override
    @Transactional
    public Result cancelOrder(Long userId, Long orderId) {
        // 查询订单
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
        if (order.getStatus() != 1 && order.getStatus() != 5) {
            return Result.error("仅待接单或待面交订单可取消");
        }

        // 更新订单状态为已取消
        order.setStatus(4);
        order.setCancelTime(new Date());
        order.setCancelType(1);  // 取消类型：1-买家取消
        order.setCancelReason("买家取消订单");
        orderMapper.updateById(order);
        // 回补库存
        restoreProductStock(order);

        log.info("订单已取消：订单号={}", order.getOrderNo());

        // 通知卖家：买家已取消订单
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
        // 调用内部方法交付
        String error = shipOrderInternal(sellerId, orderId, expressNo);
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
        // 创建批量操作结果对象
        BatchOperationResult result = new BatchOperationResult();
        // 列表为空
        if (items == null || items.isEmpty()) {
            result.addFailure(null, "请至少选择一条订单");
            return Result.success(result);
        }
        // 遍历交付项
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
        return Result.success(result);
    }

    /**
     * 确认收到
     */
    @Override
    @Transactional
    public Result confirmOrder(Long buyerId, Long orderId) {
        // 查询订单
        Order order = lockOrderForTransition(orderId);
        // 订单不存在
        if (order == null) {
            return Result.error("订单不存在");
        }
        // 当前用户不是买家
        if (!order.getBuyerId().equals(buyerId)) {
            return Result.error("无权操作");
        }
        // 订单状态不是待确认（2）
        if (order.getStatus() != 2 && order.getStatus() != 6) {
            return Result.error("仅待确认状态可确认");
        }

        // 更新订单状态为已完成
        order.setStatus(3);
        order.setFinishTime(new Date());  // 完成时间
        orderMapper.updateById(order);

        log.info("订单已确认收到：订单号={}", order.getOrderNo());

        // 通知卖家：买家已确认收到，交易完成
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
        // 调用内部方法拒绝
        String error = rejectOrderInternal(sellerId, orderId, reason);
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
        // 创建批量操作结果对象
        BatchOperationResult result = new BatchOperationResult();
        // 遍历订单ID列表
        for (Long orderId : normalizeOrderIds(orderIds)) {
            String error = rejectOrderInternal(sellerId, orderId, reason);
            if (error == null) {
                result.addSuccess(orderId);  // 成功
            } else {
                result.addFailure(orderId, error);  // 失败
            }
        }
        return Result.success(result);
    }

    /**
     * 查询待处理订单数量
     */
    @Override
    public Result getPendingCount(Long userId) {
        LambdaQueryWrapper<Order> acceptanceWrapper = new LambdaQueryWrapper<>();
        acceptanceWrapper.eq(Order::getSellerId, userId).eq(Order::getStatus, 5);
        long toAccept = orderMapper.selectCount(acceptanceWrapper);
        // 统计待面交数量（卖家视角，状态=1）
        LambdaQueryWrapper<Order> sellerWrapper = new LambdaQueryWrapper<>();
        sellerWrapper.eq(Order::getSellerId, userId).eq(Order::getStatus, 1);
        long toShip = orderMapper.selectCount(sellerWrapper);

        // 统计待确认数量（买家视角，状态=2）
        LambdaQueryWrapper<Order> buyerWrapper = new LambdaQueryWrapper<>();
        buyerWrapper.eq(Order::getBuyerId, userId).eq(Order::getStatus, 2);
        long toConfirm = orderMapper.selectCount(buyerWrapper);

        // 组装返回数据
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("toAccept", toAccept);
        data.put("toShip", toShip);  // 待交付数量
        data.put("toConfirm", toConfirm);  // 待确认数量
        return Result.success(data);
    }

    /**
     * 分页查询订单（共用方法）
     */
    private Result queryOrders(Integer page, Integer size, com.baomidou.mybatisplus.core.conditions.Wrapper<Order> wrapper) {
        // 分页查询
        Page<Order> pageObj = new Page<>(page, size);
        IPage<Order> result = orderMapper.selectPage(pageObj, wrapper);
        // 如果有数据，补齐展示字段
        if (!result.getRecords().isEmpty()) {
            enrichOrders(result.getRecords());
        }
        return Result.success(result);
    }

    /**
     * 补齐订单展示字段（商品标题、图片、买卖家昵称）
     */
    private void enrichOrders(List<Order> orders) {
        // 收集商品ID和用户ID
        Set<Long> productIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (Order order : orders) {
            productIds.add(order.getProductId());
            userIds.add(order.getBuyerId());
            userIds.add(order.getSellerId());
        }

        // 批量查询商品
        Map<Long, Product> productMap = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));
        // 批量查询商品图片（取第一张）
        List<ProductImage> allImages = productImageMapper.selectByProductIds(new ArrayList<>(productIds));
        Map<Long, String> firstImageMap = allImages.stream()
                .collect(Collectors.groupingBy(ProductImage::getProductId,
                        Collectors.mapping(ProductImage::getUrl,
                                Collectors.collectingAndThen(Collectors.toList(),
                                        list -> list.isEmpty() ? null : list.get(0)))));
        // 批量查询用户
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        // 一次批量查询数据库评价记录，刷新页面后仍能正确显示已评价。
        Map<Long, Long> completedBuyers = orders.stream()
                .filter(order -> Integer.valueOf(3).equals(order.getStatus()))
                .collect(Collectors.toMap(Order::getId, Order::getBuyerId));
        Set<Long> evaluatedIds = new HashSet<>();
        if (!completedBuyers.isEmpty()) {
            LambdaQueryWrapper<Evaluation> query = new LambdaQueryWrapper<>();
            query.in(Evaluation::getOrderId, completedBuyers.keySet())
                    .select(Evaluation::getOrderId, Evaluation::getFromUserId);
            for (Evaluation evaluation : evaluationMapper.selectList(query)) {
                if (java.util.Objects.equals(completedBuyers.get(evaluation.getOrderId()), evaluation.getFromUserId())) {
                    evaluatedIds.add(evaluation.getOrderId());
                }
            }
        }

        // 为每个订单补齐字段
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
        return Integer.valueOf(3).equals(status) || Integer.valueOf(4).equals(status);
    }

    /**
     * 规范化订单ID列表（去重、过滤空值）
     */
    private List<Long> normalizeOrderIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return new ArrayList<>();
        }
        // 过滤null和<=0的ID，并去重
        return orderIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 买家隐藏订单内部方法
     */
    private String hideBoughtOrderInternal(Long buyerId, Long orderId) {
        // 查询订单
        Order order = orderMapper.selectById(orderId);
        // 订单不存在
        if (order == null) {
            return "订单不存在";
        }
        // 当前用户不是买家
        if (!order.getBuyerId().equals(buyerId)) {
            return "无权操作";
        }
        // 只有已完成或已取消的订单可隐藏
        if (!canHideOrderRecord(order)) {
            return "仅已完成或已取消订单可删除记录";
        }
        // 已经被隐藏
        if (Integer.valueOf(1).equals(order.getBuyerDeleted())) {
            return "订单记录已删除";
        }
        // 标记为已删除
        order.setBuyerDeleted(1);
        orderMapper.updateById(order);
        log.info("买家隐藏订单记录：订单号={} 买家ID={}", order.getOrderNo(), buyerId);
        return null;  // 成功返回null
    }

    /**
     * 卖家隐藏订单内部方法
     */
    private String hideSoldOrderInternal(Long sellerId, Long orderId) {
        // 查询订单
        Order order = orderMapper.selectById(orderId);
        // 订单不存在
        if (order == null) {
            return "订单不存在";
        }
        // 当前用户不是卖家
        if (!order.getSellerId().equals(sellerId)) {
            return "无权操作";
        }
        // 只有已完成或已取消的订单可隐藏
        if (!canHideOrderRecord(order)) {
            return "仅已完成或已取消订单可删除记录";
        }
        // 已经被隐藏
        if (Integer.valueOf(1).equals(order.getSellerDeleted())) {
            return "订单记录已删除";
        }
        // 标记为已删除
        order.setSellerDeleted(1);
        orderMapper.updateById(order);
        log.info("卖家隐藏订单记录：订单号={} 卖家ID={}", order.getOrderNo(), sellerId);
        return null;  // 成功返回null
    }

    /**
     * 交付内部方法
     */
    private String shipOrderInternal(Long sellerId, Long orderId, String expressNo) {
        // 订单ID校验
        if (orderId == null || orderId <= 0) {
            return "订单 ID 无效";
        }
        // 查询订单
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
        if (order.getStatus() != 1) {
            return "请先接单，仅待面交订单可确认交付";
        }
        if (order.getMeetupTime() == null) {
            return "买家尚未确认面交时间，请先发送预约卡片并等待确认";
        }
        // 更新订单
        // 面交不生成物流凭证，也不能把卖家确认交付解释为已支付。
        order.setExpressNo(null);
        order.setStatus(2);  // 状态改为待确认
        order.setDeliverTime(new Date());  // 交付时间
        orderMapper.updateById(order);
        log.info("卖家已确认面交交付：订单号={}", order.getOrderNo());

        // 通知买家：卖家已交付
        notificationService.notifyUser(order.getBuyerId(), "order", "卖家声明已交付",
                "订单（" + order.getOrderNo() + "）卖家声明已交付，请核实是否收到；未收到或有问题可在订单中反馈。付款由双方线下完成。", order.getId());
        return null;  // 成功返回null
    }

    /**
     * 拒绝交付内部方法
     */
    private String rejectOrderInternal(Long sellerId, Long orderId, String reason) {
        // 订单ID校验
        if (orderId == null || orderId <= 0) {
            return "订单 ID 无效";
        }
        // 查询订单
        Order order = lockOrderForTransition(orderId);
        // 订单不存在
        if (order == null) {
            return "订单不存在";
        }
        // 当前用户不是卖家
        if (!order.getSellerId().equals(sellerId)) {
            return "无权操作";
        }
        if (order.getStatus() != 1 && order.getStatus() != 5) {
            return "仅待接单或待面交订单可拒绝或取消";
        }
        boolean waitingAcceptance = order.getStatus() == 5;
        // 更新订单为已取消
        order.setStatus(4);
        order.setCancelTime(new Date());
        order.setCancelType(2);  // 取消类型：2-卖家拒绝
        order.setCancelReason(normalizeRejectReason(reason));  // 拒绝原因
        orderMapper.updateById(order);
        // 回补库存
        restoreProductStock(order);
        log.info("卖家拒绝交付：卖家ID={} 订单号={} 原因={}",
                sellerId, order.getOrderNo(), reason != null ? reason : "");

        // 通知买家：卖家拒绝交付
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
        // 获取分布式锁
        RLock lock = redissonClient.getLock(ORDER_LOCK_KEY + order.getProductId());
        boolean locked = false;
        try {
            // 尝试获取锁
            locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                // 拿不到锁时记录警告，但继续回补
                log.warn("回补库存时未能获取锁（系统繁忙）：订单号={} 商品ID={}",
                        order.getOrderNo(), order.getProductId());
            }
            // 查询商品
            Product product = productMapper.selectById(order.getProductId());
            if (product == null) {
                return;
            }
            // 回补库存
            int restoreQuantity = order.getQuantity() == null ? 1 : order.getQuantity();
            int currentStock = product.getStock() == null ? 0 : product.getStock();
            product.setStock(currentStock + restoreQuantity);
            // 如果库存>0且状态是已售罄(3)，改回在售(1)
            if (product.getStock() > 0 && product.getStatus() == 3) {
                product.setStatus(1);
            }
            product.setUpdateTime(new Date());
            productMapper.updateById(product);
            // 删除商品详情缓存
            redisTemplate.delete("product:detail:" + product.getId());
            if (productChanges != null) productChanges.publish(product.getId());
        } catch (InterruptedException ex) {
            // 中断异常，恢复中断状态
            Thread.currentThread().interrupt();
        } finally {
            // 释放锁
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
