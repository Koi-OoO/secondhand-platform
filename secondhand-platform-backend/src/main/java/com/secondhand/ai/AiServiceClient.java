package com.secondhand.ai;

import com.secondhand.ai.dto.AiChatRequest;
import com.secondhand.ai.dto.AiChatResponse;
import com.secondhand.entity.User;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/** 调用内部 Python AI 服务的 HTTP 客户端。 */
@Component
public class AiServiceClient {
    private final RestTemplate restTemplate;
    private final AiServiceProperties properties;

    @Autowired
    public AiServiceClient(RestTemplateBuilder builder, AiServiceProperties properties) {
        // 用配置中的连接和读取超时构建同步 HTTP 客户端
        this(builder
                .setConnectTimeout(Duration.ofMillis(Math.max(1, properties.getConnectTimeoutMs())))
                .setReadTimeout(Duration.ofMillis(Math.max(1, properties.getReadTimeoutMs())))
                .build(), properties);
    }

    /** 单元测试使用绑定 MockRestServiceServer 的构造器。 */
    AiServiceClient(RestTemplate restTemplate, AiServiceProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public AiChatResponse chat(AiChatRequest request, User user, String traceId) {
        // 1. 校验 AI 服务地址
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new AiServiceUnavailableException();
        }

        // 2. 组装会话消息、用户上下文和链路ID
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversationId", request.getConversationId());
        payload.put("message", request.getMessage());

        Map<String, Object> userContext = new LinkedHashMap<>();
        userContext.put("userId", user.getId());
        userContext.put("campus", user.getCampus() == null ? "" : user.getCampus());
        userContext.put("role", user.getRole() == null ? "" : user.getRole());
        payload.put("userContext", userContext);
        payload.put("traceId", traceId);

        // 3. 设置内部服务令牌和追踪头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Service-Token", properties.getInternalToken());
        headers.set("X-Trace-Id", traceId);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        // 4. 调用 Python 内部接口，非2xx或空响应都视为服务不可用
        String endpoint = baseUrl.replaceAll("/+$", "") + "/internal/agent/chat";
        try {
            ResponseEntity<AiChatResponse> response = restTemplate.exchange(
                    endpoint, HttpMethod.POST, entity, AiChatResponse.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new AiServiceUnavailableException();
            }
            return response.getBody();
        } catch (RestClientException exception) {
            throw new AiServiceUnavailableException(exception);
        }
    }

    /** 统一对外表示 AI 服务不可用，由公开控制器转换为友好提示。 */
    public static class AiServiceUnavailableException extends RuntimeException {
        public AiServiceUnavailableException() {
            super("AI 服务暂时不可用");
        }

        public AiServiceUnavailableException(Throwable cause) {
            super("AI 服务暂时不可用", cause);
        }
    }
}
