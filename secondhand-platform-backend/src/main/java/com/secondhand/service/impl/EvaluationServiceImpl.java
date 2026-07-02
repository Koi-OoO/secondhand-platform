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
import com.secondhand.util.Result;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
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

    /**
     * 提交订单评价
     */
    @Override
    @Transactional  // 开启事务，保证数据一致性
    public Result evaluate(Long fromUserId, Long orderId, Integer rating, String content, Boolean anonymous) {
        // 校验评分范围：1-5分
        if (rating == null || rating < 1 || rating > 5) {
            return Result.error("评分需在 1 到 5 之间");
        }

        // 查询订单
        Order order = orderMapper.selectById(orderId);
        // 订单不存在
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

        // 检查是否已评价过该订单
        LambdaQueryWrapper<Evaluation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Evaluation::getOrderId, orderId)
                .eq(Evaluation::getFromUserId, fromUserId);
        if (evaluationMapper.selectCount(wrapper) > 0) {
            return Result.error("您已评价过该订单");
        }

        // 创建评价记录
        Evaluation ev = new Evaluation();
        ev.setOrderId(orderId);  // 订单ID
        ev.setFromUserId(fromUserId);  // 评价人ID（买家）
        ev.setToUserId(order.getSellerId());  // 被评价人ID（卖家）
        ev.setRating(rating);  // 评分
        ev.setContent(content);  // 评价内容
        ev.setAnonymous(Boolean.TRUE.equals(anonymous));  // 是否匿名
        ev.setCreateTime(new Date());  // 创建时间
        evaluationMapper.insert(ev);  // 插入数据库

        // 更新卖家信用分 = 平均评分 * 20，范围20-100
        double avg = evaluationMapper.avgRatingByUser(order.getSellerId());  // 查询平均评分
        int creditScore = (int) Math.round(avg * 20);  // 转换为信用分
        User seller = userMapper.selectById(order.getSellerId());  // 查询卖家
        if (seller != null) {
            // 限制信用分在0-100之间
            seller.setCreditScore(Math.max(0, Math.min(100, creditScore)));
            userMapper.updateById(seller);  // 更新卖家信用分
        }

        return Result.success("评价成功");
    }

    /**
     * 查询用户收到的评价列表（分页）
     */
    @Override
    public Result getUserEvaluations(Long userId, Integer page, Integer size) {
        // 页码校验，默认1
        if (page == null || page < 1) {
            page = 1;
        }
        // 每页条数校验，默认12
        if (size == null || size < 1) {
            size = 12;
        }

        // 构建查询条件：被评价人=userId，按创建时间倒序
        LambdaQueryWrapper<Evaluation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Evaluation::getToUserId, userId)
                .orderByDesc(Evaluation::getCreateTime);

        // 分页查询
        Page<Evaluation> pageObj = new Page<>(page, size);
        IPage<Evaluation> result = evaluationMapper.selectPage(pageObj, wrapper);

        // 获取评价列表
        List<Evaluation> evals = result.getRecords();
        // 如果有数据，批量关联查询相关信息
        if (!evals.isEmpty()) {
            // 1. 批量查询评价人信息
            List<Long> fromIds = evals.stream()
                    .map(Evaluation::getFromUserId)
                    .distinct()
                    .collect(Collectors.toList());
            Map<Long, User> userMap = userMapper.selectBatchIds(fromIds).stream()
                    .collect(Collectors.toMap(User::getId, u -> u));

            // 2. 批量查询订单信息
            List<Long> orderIds = evals.stream()
                    .map(Evaluation::getOrderId)
                    .distinct()
                    .collect(Collectors.toList());
            Map<Long, Order> orderMap = orderMapper.selectBatchIds(orderIds).stream()
                    .collect(Collectors.toMap(Order::getId, o -> o));

            // 3. 从订单中提取商品ID，批量查询商品信息
            List<Long> productIds = orderMap.values().stream()
                    .map(Order::getProductId)
                    .filter(id -> id != null)
                    .distinct()
                    .collect(Collectors.toList());

            // 4. 批量查询商品信息
            Map<Long, Product> productMap = productIds.isEmpty()
                    ? Collections.emptyMap()
                    : productMapper.selectBatchIds(productIds).stream()
                    .collect(Collectors.toMap(Product::getId, p -> p));

            // 5. 批量查询商品首图
            Map<Long, String> productImageMap = productIds.isEmpty()
                    ? Collections.emptyMap()
                    : productImageMapper.selectByProductIds(productIds).stream()
                    .collect(Collectors.toMap(
                            ProductImage::getProductId,
                            ProductImage::getUrl,
                            (first, ignored) -> first  // 多个图取第一个
                    ));

            // 6. 组装评价列表的展示数据
            for (Evaluation ev : evals) {
                // 匿名评价：隐藏评价人信息
                if (Boolean.TRUE.equals(ev.getAnonymous())) {
                    ev.setFromUserNickname("匿名用户");
                    ev.setFromUserAvatar(null);
                } else {
                    User u = userMap.get(ev.getFromUserId());
                    if (u != null) {
                        ev.setFromUserNickname(u.getNickname() != null ? u.getNickname() : u.getUsername());
                        ev.setFromUserAvatar(u.getAvatar());
                    }
                }

                // 补充订单快照信息（商品标题、商品图片）
                Order order = orderMap.get(ev.getOrderId());
                if (order != null) {
                    ev.setProductId(order.getProductId());
                    Product product = productMap.get(order.getProductId());
                    // 优先用订单快照的商品标题，没有则用当前商品标题
                    ev.setProductTitle(order.getProductTitle() != null
                            ? order.getProductTitle()
                            : product != null ? product.getTitle() : null);
                    // 优先用订单快照的商品图片，没有则用当前商品首图
                    ev.setProductImage(order.getProductImage() != null
                            ? order.getProductImage()
                            : productImageMap.get(order.getProductId()));
                }
            }
        }

        // 返回分页结果
        return Result.success(result);
    }
}