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

/** HTTP client for the internal Python AI service. */
@Component
public class AiServiceClient {
    private final RestTemplate restTemplate;
    private final AiServiceProperties properties;

    @Autowired
    public AiServiceClient(RestTemplateBuilder builder, AiServiceProperties properties) {
        this(builder
                .setConnectTimeout(Duration.ofMillis(Math.max(1, properties.getConnectTimeoutMs())))
                .setReadTimeout(Duration.ofMillis(Math.max(1, properties.getReadTimeoutMs())))
                .build(), properties);
    }

    /** Constructor used by focused unit tests with a bound MockRestServiceServer. */
    AiServiceClient(RestTemplate restTemplate, AiServiceProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public AiChatResponse chat(AiChatRequest request, User user, String traceId) {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new AiServiceUnavailableException();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversationId", request.getConversationId());
        payload.put("message", request.getMessage());

        Map<String, Object> userContext = new LinkedHashMap<>();
        userContext.put("userId", user.getId());
        userContext.put("campus", user.getCampus() == null ? "" : user.getCampus());
        userContext.put("role", user.getRole() == null ? "" : user.getRole());
        payload.put("userContext", userContext);
        payload.put("traceId", traceId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Service-Token", properties.getInternalToken());
        headers.set("X-Trace-Id", traceId);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        String endpoint = baseUrl.replaceAll("/+$", "") + "/internal/agent/chat";
        try {
            ResponseEntity<AiChatResponse> response = restTemplate.exchange(
                    endpoint, HttpMethod.POST, entity, AiChatResponse.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new AiServiceUnavailableException();
            }
            return response.getBody();
        } catch (RestClientException ex) {
            throw new AiServiceUnavailableException(ex);
        }
    }

    /** Stable exception translated by the public controller. */
    public static class AiServiceUnavailableException extends RuntimeException {
        public AiServiceUnavailableException() {
            super("AI 服务暂时不可用");
        }

        public AiServiceUnavailableException(Throwable cause) {
            super("AI 服务暂时不可用", cause);
        }
    }
}
