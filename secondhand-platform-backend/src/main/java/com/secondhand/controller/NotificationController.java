package com.secondhand.controller;

import com.secondhand.service.NotificationService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知模块控制器
 */
@RestController  // 标识为REST控制器，返回JSON数据
@RequestMapping("/notification")  // 请求路径前缀为 /notification
@Tag(name = "通知管理", description = "系统/互动通知的列表、未读数与已读标记，均需登录")  // Swagger文档标签
@ApiResponse(responseCode = "200", description = "统一 Result 响应",
        content = @Content(schema = @Schema(implementation = Result.class)))
public class NotificationController {

    @Autowired  // 自动注入通知服务
    private NotificationService notificationService;

    /**
     * 我的通知列表（分页）
     */
    @GetMapping("/page")  // GET /notification/page
    @Operation(summary = "我的通知列表",
            description = "需要登录，按时间倒序分页返回当前用户的通知。通知类型包括 order、favorite、system 等")
    @ApiResponse(responseCode = "200", description = "Result.data 含 records/total/current/pages")
    public Result page(@Parameter(description = "页码，从1开始", example = "1")
                       @RequestParam(defaultValue = "1") Integer page,  // 页码，默认1
                       @Parameter(description = "每页条数", example = "10")
                       @RequestParam(defaultValue = "10") Integer size,  // 每页条数，默认10
                       @Parameter(hidden = true) HttpSession session) {  // 当前会话
        // 1. 获取当前用户，校验登录状态
        Long userId = SessionUtil.getUserId(session);
        if (userId == null) {
            return Result.error("未登录");
        }

        // 2. 调用业务层，分页查询通知
        return notificationService.getMyNotifications(userId, page, size);
    }

    /**
     * 未读通知数（导航栏红点）
     */
    @GetMapping("/unread-count")  // GET /notification/unread-count
    @Operation(summary = "未读通知数", description = "需要登录，返回当前用户未读通知数量，用于导航栏红点")
    public Result unreadCount(@Parameter(hidden = true) HttpSession session) {  // 当前会话
        // 1. 获取当前用户，校验登录状态
        Long userId = SessionUtil.getUserId(session);
        if (userId == null) {
            return Result.error("未登录");
        }

        // 2. 调用业务层，查询未读通知数
        return notificationService.getUnreadCount(userId);
    }

    /**
     * 标记单条通知已读
     */
    @PutMapping("/{id}/read")  // PUT /notification/{id}/read
    @Operation(summary = "标记单条已读", description = "需要登录，仅能标记属于自己的通知")
    public Result read(@Parameter(description = "通知ID", example = "1", required = true)
                       @PathVariable Long id,  // 通知ID，从路径获取
                       @Parameter(hidden = true) HttpSession session) {  // 当前会话
        // 1. 获取当前用户，校验登录状态
        Long userId = SessionUtil.getUserId(session);
        if (userId == null) {
            return Result.error("未登录");
        }

        // 2. 调用业务层，标记单条通知已读
        return notificationService.markRead(userId, id);
    }

    /**
     * 全部标记已读
     */
    @PutMapping("/read-all")  // PUT /notification/read-all
    @Operation(summary = "全部标记已读", description = "需要登录，把当前用户所有未读通知置为已读")
    public Result readAll(@Parameter(hidden = true) HttpSession session) {  // 当前会话
        // 1. 获取当前用户，校验登录状态
        Long userId = SessionUtil.getUserId(session);
        if (userId == null) {
            return Result.error("未登录");
        }

        // 2. 调用业务层，把全部通知标记为已读
        return notificationService.markAllRead(userId);
    }
}
