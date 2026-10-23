package com.secondhand.service;

import com.secondhand.entity.OrderBatchShipItem;
import com.secondhand.util.Result;
import java.util.List;

/**
 * 订单服务接口
 * 定义订单模块的核心业务能力，覆盖下单、查询、交付、取消、
 * 确认收到、批量处理以及历史记录隐藏等场景。
 */
public interface OrderService {
    Result remindBuyer(Long sellerId, Long orderId);
    Result reportDeliveryIssue(Long buyerId, Long orderId, String issue);


    /** 卖家接单：待接单进入待面交，不再次扣库存。 */
    Result acceptOrder(Long sellerId, Long orderId);

    /** 查询当前用户与指定对方之间的订单，遵守本人隐藏标记。 */
    Result getOrdersWithUser(Long userId, Long peerId, Integer page, Integer size);

    Result getOrderDetail(Long userId, Long orderId);

    /**
     * 创建订单并扣减库存
     *
     * @param buyerId 买家用户 ID
     * @param productId 商品 ID
     * @param quantity 购买数量
     * @param address 面交地点
     * @return Result 下单结果
     */
    Result createOrder(Long buyerId, Long productId, Integer quantity, String address);

    /**
     * 分页查询买家视角订单列表
     *
     * @param buyerId 买家用户 ID
     * @param page 页码
     * @param size 每页条数
     * @return Result 订单分页结果
     */
    Result getMyBoughtOrders(Long buyerId, Integer page, Integer size);

    /**
     * 分页查询卖家视角订单列表
     *
     * @param sellerId 卖家用户 ID
     * @param page 页码
     * @param size 每页条数
     * @return Result 订单分页结果
     */
    Result getMySoldOrders(Long sellerId, Integer page, Integer size);

    /**
     * 隐藏买家视角中的单条订单记录
     *
     * @param buyerId 买家用户 ID
     * @param orderId 订单 ID
     * @return Result 处理结果
     */
    Result hideBoughtOrder(Long buyerId, Long orderId);

    /**
     * 隐藏卖家视角中的单条订单记录
     *
     * @param sellerId 卖家用户 ID
     * @param orderId 订单 ID
     * @return Result 处理结果
     */
    Result hideSoldOrder(Long sellerId, Long orderId);

    /**
     * 批量隐藏买家视角中的订单记录
     *
     * @param buyerId 买家用户 ID
     * @param orderIds 订单 ID 列表
     * @return Result 批量处理结果
     */
    Result batchHideBoughtOrders(Long buyerId, List<Long> orderIds);

    /**
     * 批量隐藏卖家视角中的订单记录
     *
     * @param sellerId 卖家用户 ID
     * @param orderIds 订单 ID 列表
     * @return Result 批量处理结果
     */
    Result batchHideSoldOrders(Long sellerId, List<Long> orderIds);

    /**
     * 买家取消订单
     *
     * @param userId 买家用户 ID
     * @param orderId 订单 ID
     * @return Result 取消结果
     */
    Result cancelOrder(Long userId, Long orderId);

    /**
     * 卖家确认实际面交交付（不要求物流单号）
     *
     * @param sellerId 卖家用户 ID
     * @param orderId 订单 ID
     * @param expressNo 旧接口兼容字段，面交不使用
     * @return Result 交付结果
     */
    Result shipOrder(Long sellerId, Long orderId, String expressNo);

    /**
     * 卖家批量交付
     *
     * @param sellerId 卖家用户 ID
     * @param items 交付项列表
     * @return Result 批量交付结果
     */
    Result batchShipOrders(Long sellerId, List<OrderBatchShipItem> items);

    /**
     * 买家确认收到
     *
     * @param buyerId 买家用户 ID
     * @param orderId 订单 ID
     * @return Result 确认结果
     */
    Result confirmOrder(Long buyerId, Long orderId);

    /**
     * 卖家拒绝交付并回滚库存
     *
     * @param sellerId 卖家用户 ID
     * @param orderId 订单 ID
     * @param reason 拒绝原因
     * @return Result 拒绝结果
     */
    Result rejectOrder(Long sellerId, Long orderId, String reason);

    /**
     * 卖家批量拒绝交付
     *
     * @param sellerId 卖家用户 ID
     * @param orderIds 订单 ID 列表
     * @param reason 拒绝原因
     * @return Result 批量处理结果
     */
    Result batchRejectOrders(Long sellerId, List<Long> orderIds, String reason);

    /**
     * 统计当前用户待处理的买卖订单数量
     *
     * @param userId 用户 ID
     * @return Result 待处理订单数量统计
     */
    Result getPendingCount(Long userId);
}
