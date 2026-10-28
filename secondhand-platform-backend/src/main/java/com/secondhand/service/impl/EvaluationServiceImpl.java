package com.secondhand.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.secondhand.entity.Evaluation;
import com.secondhand.entity.Order;
import com.secondhand.entity.Product;
import com.secondhand.entity.ProductImage;
import com.secondhand.entity.User;
import com.secondhand.mapper.EvaluationMapper;
import com.secondhand.mapper.OrderMapper;
import com.secondhand.mapper.ProductImageMapper;
import com.secondhand.mapper.ProductMapper;
import com.secondhand.mapper.UserMapper;
import com.secondhand.service.EvaluationService;
import com.secondhand.service.NotificationService;
import com.secondhand.util.Result;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 评价服务实现类
 */
@Service  // 标识为Service层组件
public class EvaluationServiceImpl implements EvaluationService {

    @Autowired  // 注入评价Mapper
    private EvaluationMapper evaluationMapper;
    @Autowired  // 注入订单Mapper
    private OrderMapper orderMapper;
    @Autowired  // 注入商品Mapper
    private ProductMapper productMapper;
    @Autowired  // 注入商品图片Mapper
    private ProductImageMapper productImageMapper;
    @Autowired  // 注入用户Mapper
    private UserMapper userMapper;
    @Autowired
    private NotificationService notificationService;

    /**
     * 提交订单评价
     */
    @Override
    @Transactional  // 开启事务，保证数据一致性
    public Result evaluate(Long fromUserId, Long orderId, Integer rating, String content, Boolean anonymous) {
        // 1. 校验评分范围和评价内容长度
        if (rating == null || rating < 1 || rating > 5) {
            return Result.error("评分需在 1 到 5 之间");
        }
        if (content != null && content.length() > 500) {
            return Result.error("评价内容不能超过500字");
        }

        // 2. 查询订单，确认订单已完成且当前用户是买家
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            return Result.error("订单不存在");
        }
        // 订单状态不是已完成（status=3）
        if (order.getStatus() != 3) {
            return Result.error("仅已完成订单可评价");
        }
        // 当前用户不是买家
        if (!order.getBuyerId().equals(fromUserId)) {
            return Result.error("仅买家可评价");
        }

        // 3. 同一买家不能重复评价同一订单
        LambdaQueryWrapper<Evaluation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Evaluation::getOrderId, orderId)
                .eq(Evaluation::getFromUserId, fromUserId);
        if (evaluationMapper.selectCount(wrapper) > 0) {
            return Result.error("您已评价过该订单");
        }

        // 4. 保存评价记录
        Evaluation evaluation = new Evaluation();
        evaluation.setOrderId(orderId);  // 订单ID
        evaluation.setFromUserId(fromUserId);  // 评价人ID（买家）
        evaluation.setToUserId(order.getSellerId());  // 被评价人ID（卖家）
        evaluation.setRating(rating);  // 评分
        evaluation.setContent(content);  // 评价内容
        evaluation.setAnonymous(Boolean.TRUE.equals(anonymous));  // 是否匿名
        evaluation.setCreateTime(new Date());  // 创建时间
        evaluationMapper.insert(evaluation);  // 插入数据库

        // 5. 通知卖家收到新评价
        notificationService.notifyUser(order.getSellerId(), "order", "收到新评价",
                "买家已评价订单，请查看评价详情", orderId);

        // 6. 返回评价结果
        return Result.success("评价成功");
    }

    /**
     * 查询用户收到的评价列表（分页）
     */
    @Override
    public Result getUserEvaluations(Long userId, Integer page, Integer size) {
        // 1. 规范页码和每页条数
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1) {
            size = 12;
        }

        // 2. 按被评价人筛选，按创建时间倒序分页查询
        LambdaQueryWrapper<Evaluation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Evaluation::getToUserId, userId)
                .orderByDesc(Evaluation::getCreateTime);

        Page<Evaluation> pageRequest = new Page<>(page, size);
        IPage<Evaluation> pageResult = evaluationMapper.selectPage(pageRequest, wrapper);

        // 3. 复制公开响应对象，避免隐藏身份时修改数据库实体
        List<Evaluation> evaluations = new ArrayList<>();
        for (Evaluation source : pageResult.getRecords()) {
            Evaluation view = new Evaluation();
            BeanUtils.copyProperties(source, view);
            evaluations.add(view);
        }

        // 4. 有数据时批量关联评价人、订单、商品和商品图片
        if (!evaluations.isEmpty()) {
            // 4.1 批量查询评价人信息
            List<Long> fromUserIds = new ArrayList<>();
            for (Evaluation evaluation : evaluations) {
                if (!fromUserIds.contains(evaluation.getFromUserId())) {
                    fromUserIds.add(evaluation.getFromUserId());
                }
            }
            List<User> fromUsers = userMapper.selectBatchIds(fromUserIds);
            Map<Long, User> userMap = new LinkedHashMap<>();
            for (User user : fromUsers) {
                userMap.put(user.getId(), user);
            }

            // 4.2 批量查询订单信息
            List<Long> orderIds = new ArrayList<>();
            for (Evaluation evaluation : evaluations) {
                if (!orderIds.contains(evaluation.getOrderId())) {
                    orderIds.add(evaluation.getOrderId());
                }
            }
            List<Order> orders = orderMapper.selectBatchIds(orderIds);
            Map<Long, Order> orderMap = new LinkedHashMap<>();
            for (Order order : orders) {
                orderMap.put(order.getId(), order);
            }

            // 4.3 从订单中提取商品ID并去重
            List<Long> productIds = new ArrayList<>();
            for (Order order : orders) {
                if (order.getProductId() != null && !productIds.contains(order.getProductId())) {
                    productIds.add(order.getProductId());
                }
            }

            // 4.4 批量查询商品信息
            Map<Long, Product> productMap = new LinkedHashMap<>();
            if (!productIds.isEmpty()) {
                List<Product> products = productMapper.selectBatchIds(productIds);
                for (Product product : products) {
                    productMap.put(product.getId(), product);
                }
            }

            // 4.5 批量查询商品图片，每个商品保留查询结果中的第一张
            Map<Long, String> productImageMap = new LinkedHashMap<>();
            if (!productIds.isEmpty()) {
                List<ProductImage> productImages = productImageMapper.selectByProductIds(productIds);
                for (ProductImage productImage : productImages) {
                    if (!productImageMap.containsKey(productImage.getProductId())) {
                        productImageMap.put(productImage.getProductId(), productImage.getUrl());
                    }
                }
            }

            // 4.6 组装评价列表的展示数据
            for (Evaluation evaluation : evaluations) {
                // 匿名评价：隐藏评价人信息
                if (Boolean.TRUE.equals(evaluation.getAnonymous())) {
                    evaluation.setFromUserNickname("匿名用户");
                    evaluation.setFromUserAvatar(null);
                } else {
                    User evaluator = userMap.get(evaluation.getFromUserId());
                    if (evaluator != null) {
                        if (evaluator.getNickname() != null) {
                            evaluation.setFromUserNickname(evaluator.getNickname());
                        } else {
                            evaluation.setFromUserNickname(evaluator.getUsername());
                        }
                        evaluation.setFromUserAvatar(evaluator.getAvatar());
                    }
                }

                // 补充订单快照信息（商品标题、商品图片）
                Order order = orderMap.get(evaluation.getOrderId());
                if (order != null) {
                    evaluation.setProductId(order.getProductId());
                    Product product = productMap.get(order.getProductId());

                    // 优先用订单快照的商品标题，没有则用当前商品标题
                    if (order.getSnapshotTitle() != null) {
                        evaluation.setProductTitle(order.getSnapshotTitle());
                    } else if (product != null) {
                        evaluation.setProductTitle(product.getTitle());
                    } else {
                        evaluation.setProductTitle(null);
                    }

                    // 新订单可能在下单时就没有封面，不能替换成卖家后来添加的图片
                    boolean hasSnapshot = order.getSnapshotTitle() != null
                            || order.getUnitPrice() != null || order.getSnapshotImage() != null;
                    if (hasSnapshot) {
                        evaluation.setProductImage(order.getSnapshotImage());
                    } else {
                        evaluation.setProductImage(productImageMap.get(order.getProductId()));
                    }
                }
            }
        }

        // 5. 清理公开响应中的关联身份字段
        for (Evaluation evaluation : evaluations) {
            // 公开列表不需要订单ID；匿名评价也不能通过用户ID直接找回真实身份
            evaluation.setOrderId(null);
            if (Boolean.TRUE.equals(evaluation.getAnonymous())) {
                evaluation.setFromUserId(null);
            }
        }
        pageResult.setRecords(evaluations);

        // 6. 返回分页结果
        return Result.success(pageResult);
    }
}
