package com.secondhand.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.secondhand.ai.dto.AiChatRequest;
import com.secondhand.ai.dto.AiChatResponse;
import com.secondhand.entity.User;
import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class AiServiceClientTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private AiServiceProperties properties;
    private AiServiceClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        properties = new AiServiceProperties();
        properties.setBaseUrl("http://ai.test");
        properties.setInternalToken("test-token");
        client = new AiServiceClient(restTemplate, properties);
    }

    @Test
    void postsContractPayloadAndMapsSuccessfulResponse() {
        server.expect(requestTo(URI.create("http://ai.test/internal/agent/chat")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", "test-token"))
                .andExpect(header("X-Trace-Id", "trace-1"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"conversationId":"c-1","message":"找教材",
                         "userContext":{"userId":7,"campus":"东校区","role":"USER"},
                         "traceId":"trace-1"}
                        """))
                .andRespond(withSuccess("""
                        {"conversationId":"c-1","answer":"找到 1 件商品","items":[],"traceId":"trace-1"}
                        """, MediaType.APPLICATION_JSON));

        AiChatRequest request = new AiChatRequest();
        request.setConversationId("c-1");
        request.setMessage("找教材");
        User user = new User();
        user.setId(7L);
        user.setCampus("东校区");
        user.setRole("USER");

        AiChatResponse response = client.chat(request, user, "trace-1");

        assertThat(response.getConversationId()).isEqualTo("c-1");
        assertThat(response.getAnswer()).isEqualTo("找到 1 件商品");
        assertThat(response.getTraceId()).isEqualTo("trace-1");
        server.verify();
    }

    @Test
    void mapsPython5xxToUnavailableException() {
        server.expect(requestTo("http://ai.test/internal/agent/chat"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        AiChatRequest request = new AiChatRequest("c-1", "找教材");
        User user = new User();
        user.setId(7L);

        assertThrows(AiServiceClient.AiServiceUnavailableException.class,
                () -> client.chat(request, user, "trace-1"));
        server.verify();
    }

    @Test
    void mapsConnectionTimeoutToUnavailableException() {
        server.expect(requestTo("http://ai.test/internal/agent/chat"))
                .andRespond(withException(new IOException("read timed out")));

        AiChatRequest request = new AiChatRequest("c-1", "找教材");
        User user = new User();
        user.setId(7L);

        assertThrows(AiServiceClient.AiServiceUnavailableException.class,
                () -> client.chat(request, user, "trace-1"));
        server.verify();
    }
}
