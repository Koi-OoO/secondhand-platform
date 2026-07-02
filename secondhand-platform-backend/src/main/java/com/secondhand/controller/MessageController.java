package com.secondhand.controller;

import com.secondhand.service.MessageService;
import com.secondhand.util.Result;
import com.secondhand.util.SessionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "私信管理", description = "买卖双方一对一私信：发送、会话列表、聊天记录、未读数，均需登录")  // Swagger文档标签
public class MessageController {

    @Autowired  // 自动注入私信服务
    private MessageService messageService;

    /**
     * 发送私信
     */
    @PostMapping("/send")  // POST /message/send
    @Operation(summary = "发送私信", description = "需要登录，向指定用户发送消息；接收方在线会通过WebSocket实时收到")
    @ApiResponse(responseCode = "200", description = "Result.data 为落库后的消息")
    public Result send(@RequestParam Long toUserId,  // 接收者用户ID
                       @RequestParam String content,  // 消息内容
                       @RequestParam(required = false) Long productId,  // 关联商品ID，可选
                       HttpSession session) {  // 当前会话
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
    @Operation(summary = "我的会话列表", description = "需要登录，返回每个对话的对方、最后一条消息与未读数")
    public Result conversations(HttpSession session) {  // 当前会话
        // 获取当前登录用户ID
        Long userId = SessionUtil.getUserId(session);
        if (userId == null) {
            return Result.error("未登录");
        }
        // 查询会话列表
        return messageService.getConversations(userId);
    }

    /**
     * 与某人的聊天记录（分页）
     */
    @GetMapping("/chat")  // GET /message/chat
    @Operation(summary = "聊天记录", description = "需要登录，分页查询与某用户的往来消息，并把对方发来的未读消息标记为已读")
    @ApiResponse(responseCode = "200", description = "Result.data 含 records/total/current/pages")
    public Result chat(@RequestParam Long peerId,  // 对方用户ID
                       @RequestParam(defaultValue = "1") Integer page,  // 页码，默认1
                       @RequestParam(defaultValue = "20") Integer size,  // 每页条数，默认20
                       HttpSession session) {  // 当前会话
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
    public Result unreadCount(HttpSession session) {  // 当前会话
        // 获取当前登录用户ID
        Long userId = SessionUtil.getUserId(session);
        if (userId == null) {
            return Result.error("未登录");
        }
        // 查询未读数
        return messageService.getUnreadCount(userId);
    }
}
