package com.secondhand.internal;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondhand.SecondhandPlatformApplication;
import java.net.URI;
import java.net.ServerSocket;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** 显式开启的本机只读验收：真实内部鉴权、共享IK结果、图片及故障状态，不调用收费模型。 */
@EnabledIfEnvironmentVariable(named = "CAMPUS_AI_ES_LIVE", matches = "true")
class AiEsSearchLiveTest {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void internalAiSearchUsesRealIkAndPreservesAuthenticationAndFailureStatus() throws Exception {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(SecondhandPlatformApplication.class)
                .web(WebApplicationType.SERVLET)
                // 只读验收不启动浏览量落库等后台定时任务，不改变正式应用调度配置。
                .initializers(application -> application.addBeanFactoryPostProcessor(beanFactory -> {
                    String scheduler = "org.springframework.context.annotation.internalScheduledAnnotationProcessor";
                    if (beanFactory.containsBeanDefinition(scheduler)) {
                        ((org.springframework.beans.factory.support.BeanDefinitionRegistry) beanFactory)
                                .removeBeanDefinition(scheduler);
                    }
                })).run("--server.address=127.0.0.1", "--server.port=0",
                        "--app.search.elasticsearch.enabled=true", "--app.search.elasticsearch.url=http://127.0.0.1:9200",
                        "--app.search.elasticsearch.index-name=campus_products",
                        "--app.search.elasticsearch.import-on-startup=false", "--app.search.canal.enabled=false",
                        "--mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl",
                        "--logging.level.root=WARN")) {
            String base = "http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port");
            String token = context.getEnvironment().getProperty("app.ai.internal-token");
            String query = "?keyword=" + java.net.URLEncoder.encode("苹果", StandardCharsets.UTF_8)
                    + "&size=10&sort=relevance";
            assertThat(get(base + "/internal/marketplace/listings/search" + query, null).statusCode()).isEqualTo(401);
            HttpResponse<String> internal = get(base + "/internal/marketplace/listings/search" + query, token);
            assertThat(internal.statusCode()).isEqualTo(200);
            JsonNode data = json.readTree(internal.body()).path("data");
            assertThat(data.path("records").size()).isPositive();
            JsonNode external = json.readTree(get(base + "/product/search" + query, null).body()).path("data");
            assertThat(data).isEqualTo(external);
            assertThat(data.path("records").get(0).path("images").size()).isPositive();
            JsonNode phones = json.readTree(get(base + "/internal/marketplace/listings/search?keyword="
                    + java.net.URLEncoder.encode("苹果手机", StandardCharsets.UTF_8) + "&sort=latest", token).body()).path("data");
            assertThat(phones.path("total").asLong()).as("真实库应至少返回一个苹果手机").isPositive();
            for (JsonNode phone : phones.path("records")) {
                assertThat(phone.path("title").asText().toLowerCase())
                        .containsAnyOf("苹果", "iphone")
                        .doesNotContain("充电器");
                assertThat(phone.path("categoryName").asText()).isEqualTo("手机");
            }
            JsonNode chargers = json.readTree(get(base + "/internal/marketplace/listings/search?keyword="
                    + java.net.URLEncoder.encode("苹果手机充电器", StandardCharsets.UTF_8), token).body()).path("data");
            // 配件结果随本地演示数据变化；有结果时验证它确实是充电器，不绑定固定条数。
            for (JsonNode charger : chargers.path("records")) {
                assertThat(charger.path("title").asText()).contains("充电器");
            }
            JsonNode model = json.readTree(get(base + "/internal/marketplace/listings/search?keyword=iPhone13", token).body()).path("data");
            // 型号结果同样允许为空；有结果时验证标题保留型号关键词。
            for (JsonNode modelItem : model.path("records")) {
                assertThat(modelItem.path("title").asText().toLowerCase()).contains("iphone");
            }
            JsonNode zeroBudget = json.readTree(get(base + "/internal/marketplace/listings/search?maxPrice=0", token).body());
            assertThat(zeroBudget.path("data").path("total").asLong()).isZero();
            assertThat(get(base + "/internal/marketplace/listings/search?page=1001&size=10", token).statusCode()).isEqualTo(400);
            RestClientTransport transport = (RestClientTransport) context.getBean(ElasticsearchClient.class)._transport();
            int closedPort;
            try (ServerSocket socket = new ServerSocket(0)) { closedPort = socket.getLocalPort(); }
            transport.restClient().setNodes(List.of(new Node(new HttpHost("127.0.0.1", closedPort, "http"))));
            try {
                assertThat(get(base + "/internal/marketplace/listings/search", token).statusCode()).isEqualTo(503);
            } finally {
                transport.restClient().setNodes(List.of(new Node(HttpHost.create("http://127.0.0.1:9200"))));
            }
            System.out.println("AI内部查询真实IK验收通过：鉴权、共享搜索结果、图片、零预算、分页边界、503均验证；未修改业务数据。");
        }
    }

    private HttpResponse<String> get(String url, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET();
        if (token != null) request.header("X-Internal-Service-Token", token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
