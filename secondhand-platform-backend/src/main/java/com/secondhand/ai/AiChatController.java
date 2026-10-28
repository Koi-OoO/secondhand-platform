package com.secondhand.ai;

import com.secondhand.ai.dto.AiChatRequest;
import com.secondhand.ai.dto.AiChatResponse;
import com.secondhand.entity.User;
import com.secondhand.util.Result;
import com.secondhand.util.SessionUtil;
import java.util.UUID;
import javax.servlet.http.HttpSession;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 面向浏览器的 AI 商品查询入口，实际请求转发给 Python 服务。 */
@RestController
@RequestMapping("/api/ai")
public class AiChatController {
    private final AiServiceClient aiServiceClient;

    public AiChatController(AiServiceClient aiServiceClient) {
        this.aiServiceClient = aiServiceClient;
    }

    @PostMapping("/chat")
    public Result chat(@RequestBody AiChatRequest request, HttpSession session) {
        // 1. 校验消息内容
        if (request == null || !StringUtils.hasText(request.getMessage())) {
            return Result.error("消息不能为空");
        }

        // 2. 获取当前登录用户
        User user = SessionUtil.getUser(session);
        if (user == null) {
            return Result.error("未登录");
        }

        // 3. 没有会话ID时创建新会话
        if (!StringUtils.hasText(request.getConversationId())) {
            request.setConversationId(UUID.randomUUID().toString());
        }

        // 4. 生成链路ID并调用 Python 服务
        String traceId = UUID.randomUUID().toString();
        try {
            AiChatResponse response = aiServiceClient.chat(request, user, traceId);
            return Result.success(response);
        } catch (AiServiceClient.AiServiceUnavailableException exception) {
            return Result.error("AI 服务暂时不可用");
        }
    }
}
