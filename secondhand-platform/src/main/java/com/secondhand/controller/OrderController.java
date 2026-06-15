package com.secondhand.controller;

import com.secondhand.entity.OrderBatchIdsRequest;
import com.secondhand.entity.OrderBatchRejectRequest;
import com.secondhand.entity.OrderBatchShipRequest;
import com.secondhand.service.OrderService;
import com.secondhand.util.Result;
import com.secondhand.util.SessionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单模块控制器
 */
@RestController  // 标识为REST控制器，返回JSON数据
@RequestMapping("/order")  // 请求路径前缀为 /order
@Tag(name = "订单管理", description = "提供订单创建、买卖双方订单查询、状态流转和历史隐藏等接口")  // Swagger文档标签
public class OrderController {

    @Autowired  // 自动注入OrderService
    private OrderService orderService;

    /**
     * 创建订单
     */
    @PostMapping("/create")  // POST请求，路径 /order/create
    @Operation(summary = "创建订单", description = "需要登录，根据商品ID、购买数量和收货地址创建订单")
    public Result create(@RequestParam Long productId,  // 商品ID
                         @RequestParam Integer quantity,  // 购买数量
                         @RequestParam String address,  // 收货地址
                         HttpSession session) {  // HttpSession获取当前用户
        // 从session获取用户ID
        Long userId = SessionUtil.getUserId(session);
        // 未登录
        if (userId == null) {
            return Result.error("未登录");
        }
        // 调用service创建订单
        return orderService.createOrder(userId, productId, quantity, address);
    }

    /**
     * 查询我买到的订单
     */
    @GetMapping("/bought")  // GET请求，路径 /order/bought
    @Operation(summary = "查询我买到的订单", description = "需要登录，分页返回当前用户作为买家的订单")
    public Result bought(@RequestParam(defaultValue = "1") Integer page,  // 页码，默认1
                         @RequestParam(defaultValue = "12") Integer size,  // 每页条数，默认12
                         HttpSession session) {
        // 获取当前登录用户ID
        Long userId = SessionUtil.getUserId(session);
        // 未登录
        if (userId == null) {
            return Result.error("未登录");
        }
        // 查询买家订单
        return orderService.getMyBoughtOrders(userId, page, size);
    }

    /**
     * 查询我卖出的订单
     */
    @GetMapping("/sold")  // GET请求，路径 /order/sold
    @Operation(summary = "查询我卖出的订单", description = "需要登录，分页返回当前用户作为卖家的订单")
    public Result sold(@RequestParam(defaultValue = "1") Integer page,  // 页码，默认1
                       @RequestParam(defaultValue = "12") Integer size,  // 每页条数，默认12
                       HttpSession session) {
        // 获取当前登录用户ID
        Long userId = SessionUtil.getUserId(session);
        // 未登录
        if (userId == null) {
            return Result.error("未登录");
        }
        // 查询卖家订单
        return orderService.getMySoldOrders(userId, page, size);
    }

    /**
     * 买家隐藏单条订单
     */
    @DeleteMapping("/{id}/buyer")  // DELETE请求，路径 /order/{id}/buyer
    @Operation(summary = "买家隐藏订单", description = "需要登录，仅买家可隐藏已完成或已取消订单的买家视角记录")
    public Result hideBought(@PathVariable Long id,  // 订单ID，从路径获取
                             HttpSession session) {
        // 获取当前用户ID
        Long userId = SessionUtil.getUserId(session);
        // 未登录
        if (userId == null) {
            return Result.error("未登录");
        }
        // 隐藏买家订单
        return orderService.hideBoughtOrder(userId, id);
    }

    /**
     * 买家批量隐藏订单
     */
    @DeleteMapping("/batch/buyer")  // DELETE请求，路径 /order/batch/buyer
    @Operation(summary = "买家批量隐藏订单", description = "需要登录，批量隐藏买家视角中的已完成或已取消订单记录")
    public Result batchHideBought(@RequestBody OrderBatchIdsRequest request,  // 请求体，包含订单ID列表
                                  HttpSession session) {
        // 获取当前用户ID
        Long userId = SessionUtil.getUserId(session);
        // 未登录
        if (userId == null) {
            return Result.error("未登录");
        }
        // 批量隐藏
        return orderService.batchHideBoughtOrders(userId, request != null ? request.getOrderIds() : null);
    }

    /**
     * 卖家隐藏单条订单
     */
    @DeleteMapping("/{id}/seller")  // DELETE请求，路径 /order/{id}/seller
    @Operation(summary = "卖家隐藏订单", description = "需要登录，仅卖家可隐藏已完成或已取消订单的卖家视角记录")
    public Result hideSold(@PathVariable Long id,  // 订单ID
                           HttpSession session) {
        // 获取当前用户ID
        Long userId = SessionUtil.getUserId(session);
        // 未登录
        if (userId == null) {
            return Result.error("未登录");
        }
        // 隐藏卖家订单
        return orderService.hideSoldOrder(userId, id);
    }

    /**
     * 卖家批量隐藏订单
     */
    @DeleteMapping("/batch/seller")  // DELETE请求，路径 /order/batch/seller
    @Operation(summary = "卖家批量隐藏订单", description = "需要登录，批量隐藏卖家视角中的已完成或已取消订单记录")
    public Result batchHideSold(@RequestBody OrderBatchIdsRequest request,  // 请求体，包含订单ID列表
                                HttpSession session) {
        // 获取当前用户ID
        Long userId = SessionUtil.getUserId(session);
        // 未登录
        if (userId == null) {
            return Result.error("未登录");
        }
        // 批量隐藏
        return orderService.batchHideSoldOrders(userId, request != null ? request.getOrderIds() : null);
    }

    /**
     * 查询待处理订单数量（待发货+待收货）
     */
    @GetMapping("/pending-count")  // GET请求，路径 /order/pending-count
    @Operation(summary = "查询待处理订单数", description = "需要登录，返回待发货和待收货订单的合计数量")
    public Result pendingCount(HttpSession session) {
        // 获取当前用户ID
        Long userId = SessionUtil.getUserId(session);
        // 未登录
        if (userId == null) {
            return Result.error("未登录");
        }
        // 查询待处理数量
        return orderService.getPendingCount(userId);
    }

    /**
     * 买家取消订单（仅待发货状态可取消）
     */
    @PutMapping("/{id}/cancel")  // PUT请求，路径 /order/{id}/cancel
    @Operation(summary = "取消订单", description = "需要登录，买家可取消待发货订单，并回补商品库存")
    public Result cancel(@PathVariable Long id,  // 订单ID
                         HttpSession session) {
        // 获取当前用户ID
        Long userId = SessionUtil.getUserId(session);
        // 未登录
        if (userId == null) {
            return Result.error("未登录");
        }
        // 取消订单
        return orderService.cancelOrder(userId, id);
    }

    /**
     * 卖家发货
     */
    @PutMapping("/{id}/ship")  // PUT请求，路径 /order/{id}/ship
    @Operation(summary = "订单发货", description = "需要登录，卖家填写快递单号后将订单标记为已发货")
    public Result ship(@PathVariable Long id,  // 订单ID
                       @RequestParam String expressNo,  // 快递单号
                       HttpSession session) {
        // 获取当前用户ID
        Long userId = SessionUtil.getUserId(session);
        // 未登录
        if (userId == null) {
            return Result.error("未登录");
        }
        // 发货
        return orderService.shipOrder(userId, id, expressNo);
    }

    /**
     * 批量发货
     */
    @PutMapping("/batch/ship")  // PUT请求，路径 /order/batch/ship
    @Operation(summary = "批量发货", description = "需要登录，卖家可批量提交多个订单的快递单号")
    public Result batchShip(@RequestBody OrderBatchShipRequest request,  // 请求体，包含订单ID和快递单号列表
                            HttpSession session) {
        // 获取当前用户ID
        Long userId = SessionUtil.getUserId(session);
        // 未登录
        if (userId == null) {
            return Result.error("未登录");
        }
        // 批量发货
        return orderService.batchShipOrders(userId, request != null ? request.getItems() : null);
    }

    /**
     * 卖家拒绝发货
     */
    @PutMapping("/{id}/reject")  // PUT请求，路径 /order/{id}/reject
    @Operation(summary = "拒绝发货", description = "需要登录，卖家可拒绝待发货订单，并回补商品库存")
    public Result reject(@PathVariable Long id,  // 订单ID
                         @RequestParam(required = false) String reason,  // 拒绝原因，可选
                         HttpSession session) {
        // 获取当前用户ID
        Long userId = SessionUtil.getUserId(session);
        // 未登录
        if (userId == null) {
            return Result.error("未登录");
        }
        // 拒绝发货
        return orderService.rejectOrder(userId, id, reason);
    }

    /**
     * 批量拒绝发货
     */
    @PutMapping("/batch/reject")  // PUT请求，路径 /order/batch/reject
    @Operation(summary = "批量拒绝发货", description = "需要登录，卖家可批量拒绝待发货订单并回补库存")
    public Result batchReject(@RequestBody OrderBatchRejectRequest request,  // 请求体，包含订单ID列表和拒绝原因
                              HttpSession session) {
        // 获取当前用户ID
        Long userId = SessionUtil.getUserId(session);
        // 未登录
        if (userId == null) {
            return Result.error("未登录");
        }
        // 批量拒绝
        return orderService.batchRejectOrders(userId,
                request != null ? request.getOrderIds() : null,
                request != null ? request.getReason() : null);
    }

    /**
     * 买家确认收货
     */
    @PutMapping("/{id}/confirm")  // PUT请求，路径 /order/{id}/confirm
    @Operation(summary = "确认收货", description = "需要登录，买家确认收到商品后订单完成")
    public Result confirm(@PathVariable Long id,  // 订单ID
                          HttpSession session) {
        // 获取当前用户ID
        Long userId = SessionUtil.getUserId(session);
        // 未登录
        if (userId == null) {
            return Result.error("未登录");
        }
        // 确认收货
        return orderService.confirmOrder(userId, id);
    }
}