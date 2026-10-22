package com.secondhand.controller;

import com.secondhand.entity.OrderBatchIdsRequest;
import com.secondhand.entity.OrderBatchRejectRequest;
import com.secondhand.entity.OrderBatchShipRequest;
import com.secondhand.service.OrderService;
import com.secondhand.util.Result;
import com.secondhand.util.SessionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
@ApiResponse(responseCode = "200", description = "统一 Result 响应",
        content = @Content(schema = @Schema(implementation = Result.class)))
public class OrderController {

    @Autowired  // 自动注入OrderService
    private OrderService orderService;

    /**
     * 创建订单
     */
    @PostMapping("/create")  // POST请求，路径 /order/create
    @Operation(summary = "创建订单", description = "需要登录，根据商品ID、购买数量和面交地点创建订单")
    public Result create(@Parameter(description = "商品ID", example = "100", required = true)
                         @RequestParam Long productId,  // 商品ID
                         @Parameter(description = "购买数量，必须大于0且不能超过库存", example = "1", required = true)
                         @RequestParam Integer quantity,  // 购买数量
                         @Parameter(description = "面交地点", example = "东区食堂门口", required = true)
                         @RequestParam String address,  // 面交地点
                         @Parameter(hidden = true) HttpSession session) {  // HttpSession获取当前用户
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
    public Result bought(@Parameter(description = "页码，从1开始", example = "1")
                         @RequestParam(defaultValue = "1") Integer page,  // 页码，默认1
                         @Parameter(description = "每页条数", example = "12")
                         @RequestParam(defaultValue = "12") Integer size,  // 每页条数，默认12
                         @Parameter(hidden = true) HttpSession session) {
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
    public Result sold(@Parameter(description = "页码，从1开始", example = "1")
                       @RequestParam(defaultValue = "1") Integer page,  // 页码，默认1
                       @Parameter(description = "每页条数", example = "12")
                       @RequestParam(defaultValue = "12") Integer size,  // 每页条数，默认12
                       @Parameter(hidden = true) HttpSession session) {
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
    @Operation(summary = "买家隐藏订单", description = "需要登录，仅买家可隐藏已完成或已取消订单的买家视角记录，隐藏后卖家仍可见")
    public Result hideBought(@Parameter(description = "订单ID", example = "1", required = true)
                             @PathVariable Long id,  // 订单ID，从路径获取
                             @Parameter(hidden = true) HttpSession session) {
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
    public Result batchHideBought(@io.swagger.v3.oas.annotations.parameters.RequestBody(
                                      description = "订单ID列表，只处理当前买家的已完成或已取消订单",
                                      required = true,
                                      content = @Content(schema = @Schema(implementation = OrderBatchIdsRequest.class)))
                                  @RequestBody OrderBatchIdsRequest request,  // 请求体，包含订单ID列表
                                  @Parameter(hidden = true) HttpSession session) {
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
    @Operation(summary = "卖家隐藏订单", description = "需要登录，仅卖家可隐藏已完成或已取消订单的卖家视角记录，隐藏后买家仍可见")
    public Result hideSold(@Parameter(description = "订单ID", example = "1", required = true)
                           @PathVariable Long id,  // 订单ID
                           @Parameter(hidden = true) HttpSession session) {
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
    public Result batchHideSold(@io.swagger.v3.oas.annotations.parameters.RequestBody(
                                    description = "订单ID列表，只处理当前卖家的已完成或已取消订单",
                                    required = true,
                                    content = @Content(schema = @Schema(implementation = OrderBatchIdsRequest.class)))
                                @RequestBody OrderBatchIdsRequest request,  // 请求体，包含订单ID列表
                                @Parameter(hidden = true) HttpSession session) {
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
     * 查询待处理订单数量（待交付+待确认）
     */
    @GetMapping("/pending-count")  // GET请求，路径 /order/pending-count
    @Operation(summary = "查询待处理订单数", description = "需要登录，返回待交付和待确认订单的合计数量")
    public Result pendingCount(@Parameter(hidden = true) HttpSession session) {
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
     * 买家取消订单（仅待交付状态可取消）
     */
    @PutMapping("/{id}/cancel")  // PUT请求，路径 /order/{id}/cancel
    @Operation(summary = "取消订单", description = "需要登录，买家可取消待交付订单，并回补商品库存")
    public Result cancel(@Parameter(description = "订单ID", example = "1", required = true)
                         @PathVariable Long id,  // 订单ID
                         @Parameter(hidden = true) HttpSession session) {
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
     * 卖家交付
     */
    @PutMapping({"/{id}/deliver", "/{id}/ship"})  // ship路径保留兼容，当前业务统一为面交交付
    @Operation(summary = "确认面交交付", description = "仅卖家实际交付商品后可确认，不要求物流单号，不代表平台确认付款")
    public Result ship(@Parameter(description = "订单ID", example = "1", required = true)
                       @PathVariable Long id,  // 订单ID
                       @Parameter(description = "旧接口兼容字段，面交不使用")
                       @RequestParam(required = false) String expressNo,
                       @Parameter(hidden = true) HttpSession session) {
        // 获取当前用户ID
        Long userId = SessionUtil.getUserId(session);
        // 未登录
        if (userId == null) {
            return Result.error("未登录");
        }
        // 交付
        return orderService.shipOrder(userId, id, expressNo);
    }

    /**
     * 批量交付
     */
    @PutMapping({"/batch/deliver", "/batch/ship"})
    @Operation(summary = "批量确认面交交付", description = "逐项检查卖家身份及待交付状态，不要求物流单号")
    public Result batchShip(@io.swagger.v3.oas.annotations.parameters.RequestBody(
                                description = "批量交付列表，每项包含 orderId",
                                required = true,
                                content = @Content(schema = @Schema(implementation = OrderBatchShipRequest.class)))
                            @RequestBody OrderBatchShipRequest request,  // 请求体，包含订单ID列表
                            @Parameter(hidden = true) HttpSession session) {
        // 获取当前用户ID
        Long userId = SessionUtil.getUserId(session);
        // 未登录
        if (userId == null) {
            return Result.error("未登录");
        }
        // 批量交付
        return orderService.batchShipOrders(userId, request != null ? request.getItems() : null);
    }

    /**
     * 卖家拒绝交付
     */
    @PutMapping("/{id}/reject")  // PUT请求，路径 /order/{id}/reject
    @Operation(summary = "拒绝交付", description = "需要登录，卖家可拒绝待交付订单，并回补商品库存")
    public Result reject(@Parameter(description = "订单ID", example = "1", required = true)
                         @PathVariable Long id,  // 订单ID
                         @Parameter(description = "拒绝原因，可选", example = "商品已损坏")
                         @RequestParam(required = false) String reason,  // 拒绝原因，可选
                         @Parameter(hidden = true) HttpSession session) {
        // 获取当前用户ID
        Long userId = SessionUtil.getUserId(session);
        // 未登录
        if (userId == null) {
            return Result.error("未登录");
        }
        // 拒绝交付
        return orderService.rejectOrder(userId, id, reason);
    }

    /**
     * 批量拒绝交付
     */
    @PutMapping("/batch/reject")  // PUT请求，路径 /order/batch/reject
    @Operation(summary = "批量拒绝交付", description = "需要登录，卖家可批量拒绝待交付订单并回补库存")
    public Result batchReject(@io.swagger.v3.oas.annotations.parameters.RequestBody(
                                  description = "批量拒绝请求，包含订单ID列表和统一拒绝原因",
                                  required = true,
                                  content = @Content(schema = @Schema(implementation = OrderBatchRejectRequest.class)))
                              @RequestBody OrderBatchRejectRequest request,  // 请求体，包含订单ID列表和拒绝原因
                              @Parameter(hidden = true) HttpSession session) {
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
     * 买家确认收到
     */
    @PutMapping("/{id}/confirm")  // PUT请求，路径 /order/{id}/confirm
    @Operation(summary = "确认收到", description = "需要登录，买家确认收到商品后订单完成")
    public Result confirm(@Parameter(description = "订单ID", example = "1", required = true)
                          @PathVariable Long id,  // 订单ID
                          @Parameter(hidden = true) HttpSession session) {
        // 获取当前用户ID
        Long userId = SessionUtil.getUserId(session);
        // 未登录
        if (userId == null) {
            return Result.error("未登录");
        }
        // 确认收到
        return orderService.confirmOrder(userId, id);
    }
}
