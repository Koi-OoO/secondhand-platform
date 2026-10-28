package com.secondhand.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 浏览器发起一轮 AI 对话的请求。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequest {
    /** 会话ID；为空时由 Java 端创建。 */
    private String conversationId;

    /** 用户本轮发送的消息。 */
    private String message;
}
