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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
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

    /** Python 返回的真实封面与筛选条件应完整保留到对外响应。 */
    @Test
    void preservesCoverAndSearchFiltersFromPythonResponse() {
        server.expect(requestTo("http://ai.test/internal/agent/chat"))
                .andRespond(withSuccess("""
                        {"conversationId":"c-1","answer":"找到 1 件商品",
                         "items":[{"productId":10,"title":"台灯","price":89.05,"stock":1,
                                   "conditionLevel":2,"tradeLocation":"东校区",
                                   "detailPath":"/product/10","coverUrl":"/uploads/lamp.jpg"}],
                         "searchFilters":{"keyword":"台灯","categoryId":3,"minPrice":0,
                                          "maxPrice":100.05,"conditionLevel":2,"sort":"price_asc"},
                         "traceId":"trace-1"}
                        """, MediaType.APPLICATION_JSON));
        User user = new User();
        user.setId(7L);

        AiChatResponse response = client.chat(new AiChatRequest("c-1", "找100.05元以内的台灯"),
                user, "trace-1");
        // 再序列化代表控制器输出，避免新增字段只在中间 JSON 中存在。
        JsonNode output = new ObjectMapper().valueToTree(response);

        assertThat(output.path("items").get(0).path("coverUrl").asText()).isEqualTo("/uploads/lamp.jpg");
        JsonNode filters = output.path("searchFilters");
        assertThat(filters.path("keyword").asText()).isEqualTo("台灯");
        assertThat(filters.path("categoryId").asInt()).isEqualTo(3);
        assertThat(filters.path("minPrice").isNumber()).isTrue();
        assertThat(filters.path("minPrice").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(filters.path("maxPrice").decimalValue()).isEqualByComparingTo(new BigDecimal("100.05"));
        assertThat(filters.path("conditionLevel").asInt()).isEqualTo(2);
        assertThat(filters.path("sort").asText()).isEqualTo("price_asc");
        server.verify();
    }

    /** 老版本 Python 响应缺少新增可选字段时，原有商品卡仍可正常返回。 */
    @Test
    void acceptsLegacyResponseWithoutCoverOrSearchFilters() {
        server.expect(requestTo("http://ai.test/internal/agent/chat"))
                .andRespond(withSuccess("""
                        {"conversationId":"c-1","answer":"找到 1 件商品",
                         "items":[{"productId":10,"title":"台灯","price":89,"stock":1,
                                   "conditionLevel":2,"tradeLocation":"东校区","detailPath":"/product/10"}],
                         "traceId":"trace-1"}
                        """, MediaType.APPLICATION_JSON));
        User user = new User();
        user.setId(7L);

        AiChatResponse response = client.chat(new AiChatRequest("c-1", "找台灯"), user, "trace-1");
        JsonNode output = new ObjectMapper().valueToTree(response);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getProductId()).isEqualTo(10L);
        assertThat(response.getItems().get(0).getTitle()).isEqualTo("台灯");
        assertThat(response.getItems().get(0).getDetailPath()).isEqualTo("/product/10");
        assertThat(output.path("items").get(0).hasNonNull("coverUrl")).isFalse();
        assertThat(output.hasNonNull("searchFilters")).isFalse();
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
