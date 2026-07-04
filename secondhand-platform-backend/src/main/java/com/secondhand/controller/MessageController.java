package com.secondhand.controller;

import com.secondhand.service.MessageService;
import com.secondhand.util.Result;
import com.secondhand.util.SessionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 私信模块控制器
 */
@RestController  // 标识为REST控制器，返回JSON数据
@RequestMapping("/message")  // 请求路径前缀为 /message
@Tag(name = "私信管理", description = "买卖双方一对一私信：会话创建、发送、会话列表、聊天记录、在线状态和未读数，均需登录")  // Swagger文档标签
@ApiResponse(responseCode = "200", description = "统一 Result 响应",
        content = @Content(schema = @Schema(implementation = Result.class)))
public class MessageController {

    @Autowired  // 自动注入私信服务
    private MessageService messageService;

    /**
     * 发送私信
     */
    @PostMapping("/send")  // POST /message/send
    @Operation(summary = "发送私信",
            description = "需要登录，向指定用户发送消息；消息会落库并更新会话最后一条消息，接收方在线会通过 WebSocket 收到 {type:'chat',data:{...}}")
    @ApiResponse(responseCode = "200", description = "Result.data 为落库后的消息")
    public Result send(@Parameter(description = "接收者用户ID", example = "2", required = true)
                       @RequestParam Long toUserId,  // 接收者用户ID
                       @Parameter(description = "消息内容，去除首尾空格后不能为空", example = "你好，这个还在吗？", required = true)
                       @RequestParam String content,  // 消息内容
                       @Parameter(description = "关联商品ID，可选；从商品详情发起聊天时传入", example = "100")
                       @RequestParam(required = false) Long productId,  // 关联商品ID，可选
                       @Parameter(hidden = true) HttpSession session) {  // 当前会话
        // 获取当前登录用户ID
        Long userId = SessionUtil.getUserId(session);
        if (userId == null) {
            return Result.error("未登录");
        }
        // 调用service发送
        return messageService.send(userId, toUserId, content, productId);
    }

    /**
     * 我的会话列表
     */
    @GetMapping("/conversations")  // GET /message/conversations
    @Operation(summary = "我的会话列表",
            description = "需要登录，返回当前用户所有私信会话；包含尚未发送消息的空会话。Result.data 每项包含 conversationId、peerId、peerNickname、peerAvatar、productId、lastContent、lastTime、unreadCount")
    public Result conversations(@Parameter(hidden = true) HttpSession session) {  // 当前会话
        // 获取当前登录用户ID
        Long userId = SessionUtil.getUserId(session);
        if (userId == null) {
            return Result.error("未登录");
        }
        // 查询会话列表
        return messageService.getConversations(userId);
    }

    /**
     * 创建或获取会话。
     */
    @PostMapping("/conversation")  // POST /message/conversation
    @Operation(summary = "创建或获取会话",
            description = "需要登录，点击联系卖家时先创建或获取一条持久会话；即使还未发送消息也会形成空会话，刷新后仍可见。Result.data 返回 conversationId、peerId、peerNickname、peerAvatar、productId、lastContent、lastTime、unreadCount")
    public Result createConversation(@Parameter(description = "对方用户ID，不能是当前登录用户", example = "2", required = true)
                                     @RequestParam Long peerId,  // 对方用户ID
                                     @Parameter(description = "关联商品ID，可选；从商品详情联系卖家时传入", example = "100")
                                     @RequestParam(required = false) Long productId,  // 关联商品ID，可选
                                     @Parameter(hidden = true) HttpSession session) {  // 当前会话
        Long userId = SessionUtil.getUserId(session);
        if (userId == null) {
            return Result.error("未登录");
        }
        return messageService.createOrGetConversation(userId, peerId, productId);
    }

    /**
     * 与某人的聊天记录（分页）
     */
    @GetMapping("/chat")  // GET /message/chat
    @Operation(summary = "聊天记录", description = "需要登录，分页查询与某用户的往来消息，并把对方发给我的未读消息标记为已读")
    @ApiResponse(responseCode = "200", description = "Result.data 含 records/total/current/pages")
    public Result chat(@Parameter(description = "对方用户ID", example = "2", required = true)
                       @RequestParam Long peerId,  // 对方用户ID
                       @Parameter(description = "页码，从1开始", example = "1")
                       @RequestParam(defaultValue = "1") Integer page,  // 页码，默认1
                       @Parameter(description = "每页条数", example = "20")
                       @RequestParam(defaultValue = "20") Integer size,  // 每页条数，默认20
                       @Parameter(hidden = true) HttpSession session) {  // 当前会话
        // 获取当前登录用户ID
        Long userId = SessionUtil.getUserId(session);
        if (userId == null) {
            return Result.error("未登录");
        }
        // 查询聊天记录
        return messageService.getChat(userId, peerId, page, size);
    }

    /**
     * 私信总未读数（导航栏红点）
     */
    @GetMapping("/unread-count")  // GET /message/unread-count
    @Operation(summary = "私信未读数", description = "需要登录，返回当前用户收到的未读私信总数")
    public Result unreadCount(@Parameter(hidden = true) HttpSession session) {  // 当前会话
        // 获取当前登录用户ID
        Long userId = SessionUtil.getUserId(session);
        if (userId == null) {
            return Result.error("未登录");
        }
        // 查询未读数
        return messageService.getUnreadCount(userId);
    }

    /**
     * 批量查询用户在线状态。
     */
    @GetMapping("/online-status")  // GET /message/online-status
    @Operation(summary = "批量查询在线状态",
            description = "需要登录，批量返回用户在线状态。Result.data 是以用户ID为key的对象，值包含 online(Boolean，是否有 WebSocket 在线连接) 和 lastActiveTime(Date，最后活跃时间，可为空)")
    public Result onlineStatus(@Parameter(description = "用户ID列表，支持逗号分隔传参，如 userIds=2,3,4", example = "2,3,4", required = true)
                               @RequestParam List<Long> userIds,
                               @Parameter(hidden = true) HttpSession session) {
        Long userId = SessionUtil.getUserId(session);
        if (userId == null) {
            return Result.error("未登录");
        }
        return messageService.getOnlineStatus(userIds);
    }
}
