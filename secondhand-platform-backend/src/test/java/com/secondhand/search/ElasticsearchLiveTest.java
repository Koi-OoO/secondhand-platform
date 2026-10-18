package com.secondhand.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.transport.ElasticsearchTransport;
import com.secondhand.SecondhandPlatformApplication;
import com.secondhand.config.ElasticsearchConfig;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 显式提供本机 ES 地址后才运行，默认测试无需启动 ES；不触碰 MySQL 商品数据。 */
@EnabledIfEnvironmentVariable(named = "CAMPUS_ES_URL", matches = ".+")
class ElasticsearchLiveTest {

    @Test
    @Timeout(60)
    void realElasticsearchSupportsChineseCategoryFilteringAndExactPriceRoundTrip() throws Exception {
        ElasticsearchProperties properties = localProperties();
        String testIndex = "campus_products_test_" + UUID.randomUUID().toString().replace("-", "");
        properties.setIndexName(testIndex);
        try (ElasticsearchTransport transport = new ElasticsearchConfig().elasticsearchTransport(properties)) {
            ElasticsearchClient client = new ElasticsearchClient(transport);
            try {
                ProductSearchIndexInitializer initializer = new ProductSearchIndexInitializer(client, properties);
                assertThat(initializer.initialize()).isTrue();
                assertThat(initializer.initialize()).isFalse();
                assertThat(client.indices().analyze(builder -> builder.analyzer("ik_smart").text("苹果手机充电器"))
                        .tokens()).extracting(token -> token.token()).containsExactly("苹果", "手机", "充电器");
                client.index(builder -> builder.index(testIndex).id("phone").refresh(Refresh.WaitFor)
                        .document(Map.of("id", 1L, "title", "苹果手机 iPhone 13", "categoryId", 102,
                                "categoryPathIds", List.of(101, 102), "priceCents", 179999L,
                                "createTime", 1700000000000L, "status", 1)));
                client.index(builder -> builder.index(testIndex).id("charger").refresh(Refresh.WaitFor)
                        .document(Map.of("id", 2L, "title", "苹果手机充电器", "categoryId", 103,
                                "categoryPathIds", List.of(101, 103), "priceCents", 5000L, "status", 1)));
                var response = client.search(builder -> builder.index(testIndex).query(query -> query.bool(bool -> bool
                        .must(must -> must.match(match -> match.field("title").query("苹果手机")))
                        .filter(filter -> filter.term(term -> term.field("categoryPathIds").value(102))))), Map.class);
                assertThat(response.hits().total().value()).isEqualTo(1);
                assertThat(response.hits().hits().get(0).id()).isEqualTo("phone");
                assertThat(((Number) response.hits().hits().get(0).source().get("priceCents")).longValue())
                        .isEqualTo(179999L);
                assertThat(client.search(builder -> builder.index(testIndex)
                        .query(query -> query.term(term -> term.field("categoryPathIds").value(101))), Map.class)
                        .hits().total().value()).isEqualTo(2);
                assertThatThrownBy(() -> client.index(builder -> builder.index(testIndex).id("bad")
                        .document(Map.of("unknownField", "must reject"))))
                        .isInstanceOf(ElasticsearchException.class);
                System.out.println("真实 ES：中文分词、重复初始化、分类路径过滤、分单位价格、严格字段校验均通过。");
            } finally {
                // 只清理当前随机测试索引，禁止通配符或删除正式商品索引。
                if (testIndex.startsWith("campus_products_test_")
                        && client.indices().exists(builder -> builder.index(testIndex)).value()) {
                    client.indices().delete(builder -> builder.index(testIndex));
                }
            }
        }
    }

    @Test
    @Timeout(90)
    @EnabledIfEnvironmentVariable(named = "CAMPUS_ES_INITIALIZE_PRODUCT_INDEX", matches = "true")
    void realApplicationStartsWithEsDisabledAndInitializesFormalIndexWhenEnabled() {
        ElasticsearchProperties properties = localProperties();
        String uploadPath = Path.of("target", "es-smoke-upload").toAbsolutePath().toString();
        try (ConfigurableApplicationContext context = application(false, properties, uploadPath)) {
            assertThat(context.getBeansOfType(ElasticsearchClient.class)).isEmpty();
            assertThat(context.getBeansOfType(RestClient.class)).isEmpty();
            System.out.println("真实 Spring Boot：关闭 ES 时不注册客户端或连接池，传统应用正常启动。");
        }
        try (ConfigurableApplicationContext context = application(true, properties, uploadPath)) {
            assertThat(context.getBeansOfType(ElasticsearchClient.class)).hasSize(1);
            ElasticsearchClient client = context.getBean(ElasticsearchClient.class);
            assertThat(client.indices().exists(builder -> builder.index(properties.getIndexName())).value()).isTrue();
            assertThat(context.getBean(ProductSearchIndexInitializer.class).initialize()).isFalse();
            System.out.println("真实 Spring Boot：启用 ES 自动创建/验证 campus_products_v2，重复初始化不重建。");
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private ConfigurableApplicationContext application(boolean enabled, ElasticsearchProperties properties, String uploadPath) {
        return new SpringApplicationBuilder(SecondhandPlatformApplication.class).web(WebApplicationType.NONE)
                .run("--app.search.elasticsearch.enabled=" + enabled,
                        "--app.search.elasticsearch.url=" + properties.getUrl(),
                        "--app.upload.local-path=" + uploadPath,
                        "--spring.main.banner-mode=off", "--logging.level.root=WARN");
    }

    private ElasticsearchProperties localProperties() {
        String endpoint = System.getenv("CAMPUS_ES_URL");
        URI uri = URI.create(endpoint);
        assertThat(uri.getHost()).isIn("localhost", "127.0.0.1", "::1", "[::1]");
        ElasticsearchProperties properties = new ElasticsearchProperties();
        properties.setUrl(endpoint);
        return properties;
    }
}
