package com.secondhand.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.ElasticsearchTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.secondhand.config.ElasticsearchConfig;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 通过真实 HTTP 客户端访问本地协议替身，测试初始化行为，不依赖已运行的 ES。 */
class ElasticsearchFoundationTest {
    private final ObjectMapper json = new ObjectMapper();
    private final List<String> requests = new ArrayList<>();
    private HttpServer server;
    private boolean exists;
    private boolean ikAvailable = true;
    private boolean foreignMapping;
    private String disabledField;
    private JsonNode createdBody;
    private boolean wrongSearchAnalyzer;
    private boolean legacyMapping;
    private String unavailableAnalyzer;

    @Test
    void missingIkSmartFailsBeforeCreatingAnyIndex() throws Exception {
        ElasticsearchProperties p = startServer();
        unavailableAnalyzer = "ik_smart";
        try (ElasticsearchTransport transport = new ElasticsearchConfig().elasticsearchTransport(p)) {
            assertThatThrownBy(() -> new ProductSearchIndexInitializer(new ElasticsearchClient(transport), p).initialize())
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("IK");
            assertThat(requests.stream().noneMatch(item -> item.startsWith("PUT") || item.startsWith("DELETE"))).isTrue();
        }
    }

    @Test
    void oldSchemaVersionIsRejectedWithoutOverwriting() throws Exception {
        ElasticsearchProperties p = startServer();
        exists = true;
        legacyMapping = true;
        try (ElasticsearchTransport transport = new ElasticsearchConfig().elasticsearchTransport(p)) {
            assertThatThrownBy(() -> new ProductSearchIndexInitializer(new ElasticsearchClient(transport), p).initialize())
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("索引结构");
            assertThat(requests.stream().noneMatch(item -> item.startsWith("PUT") || item.startsWith("DELETE"))).isTrue();
        }
    }

    @Test
    void newSchemaUsesIkForIndexingAndSmartForSearching() throws Exception {
        ElasticsearchProperties p = startServer();
        try (ElasticsearchTransport transport = new ElasticsearchConfig().elasticsearchTransport(p)) {
            new ProductSearchIndexInitializer(new ElasticsearchClient(transport), p).initialize();
            assertThat(createdBody.at("/mappings/_meta/schema_version").asText()).isEqualTo("2");
            assertThat(createdBody.at("/mappings/properties/title/analyzer").asText()).isEqualTo("ik_max_word");
            assertThat(createdBody.at("/mappings/properties/title/search_analyzer").asText()).isEqualTo("ik_smart");
            assertThat(createdBody.at("/mappings/properties/description/search_analyzer").asText()).isEqualTo("ik_smart");
        }
    }

    @Test
    void existingIndexWithWrongSearchAnalyzerIsRejected() throws Exception {
        ElasticsearchProperties p = startServer();
        exists = true;
        wrongSearchAnalyzer = true;
        try (ElasticsearchTransport transport = new ElasticsearchConfig().elasticsearchTransport(p)) {
            assertThatThrownBy(() -> new ProductSearchIndexInitializer(new ElasticsearchClient(transport), p).initialize())
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("索引结构");
        }
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void disabledSearchCreatesNoClientOrInitializer() {
        new ApplicationContextRunner().withUserConfiguration(ElasticsearchConfig.class)
                .withPropertyValues("app.search.elasticsearch.enabled=false",
                        "app.search.elasticsearch.url=http://127.0.0.1:1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ElasticsearchClient.class);
                    assertThat(context).doesNotHaveBean(ProductSearchIndexInitializer.class);
                });
    }

    @Test
    void configurationRejectsInvalidEndpointIndexTimeoutAndPartialCredentials() {
        ElasticsearchProperties p = new ElasticsearchProperties();
        p.setUrl("http://user:do-not-log-this@localhost:9200");
        assertThatThrownBy(p::validate).isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("do-not-log-this");
        p.setUrl("http://localhost:9200/some-path");
        assertThatThrownBy(p::validate).isInstanceOf(IllegalArgumentException.class);
        p.setUrl("http://localhost:9200");
        p.setIndexName("Bad Index");
        assertThatThrownBy(p::validate).isInstanceOf(IllegalArgumentException.class);
        p.setIndexName("campus_products_v2");
        p.setSocketTimeoutMillis(0);
        assertThatThrownBy(p::validate).isInstanceOf(IllegalArgumentException.class);
        p.setSocketTimeoutMillis(5000);
        p.setUsername("elastic");
        assertThatThrownBy(p::validate).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabledClientUsesOwnMapperAndClosesWithContext() throws Exception {
        ElasticsearchProperties p = startServer();
        ObjectMapper applicationMapper = new ObjectMapper();
        new ApplicationContextRunner().withUserConfiguration(ElasticsearchConfig.class)
                .withBean(ObjectMapper.class, () -> applicationMapper)
                .withPropertyValues("app.search.elasticsearch.enabled=true",
                        "app.search.elasticsearch.url=" + p.getUrl())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ElasticsearchClient.class);
                    assertThat(context).hasSingleBean(ProductSearchIndexInitializer.class);
                    assertThat(context.getBean(ObjectMapper.class)).isSameAs(applicationMapper);
                    assertThat(context.getBean(ElasticsearchClient.class).info().version().number())
                            .isEqualTo("8.19.23");
                });
    }

    @Test
    void missingIndexIsCreatedWithChineseAnalysisExactPricesAndCategoryPath() throws Exception {
        ElasticsearchProperties p = startServer();
        try (ElasticsearchTransport transport = new ElasticsearchConfig().elasticsearchTransport(p)) {
            ProductSearchIndexInitializer initializer = new ProductSearchIndexInitializer(new ElasticsearchClient(transport), p);
            assertThat(initializer.initialize()).isTrue();
            assertThat(createdBody.at("/mappings/dynamic").asText()).isEqualTo("strict");
            assertThat(createdBody.at("/mappings/properties/title/analyzer").asText()).isEqualTo("ik_max_word");
            assertThat(createdBody.at("/mappings/properties/description/analyzer").asText()).isEqualTo("ik_max_word");
            assertThat(createdBody.at("/mappings/properties/priceCents/type").asText()).isEqualTo("long");
            assertThat(createdBody.at("/mappings/properties/categoryPathIds/type").asText()).isEqualTo("integer");
            assertThat(createdBody.at("/mappings/properties/createTime/format").asText()).isEqualTo("epoch_millis");
            assertThat(requests).contains("POST /_analyze", "PUT /campus_products_v2");
            assertThat(requests.stream().noneMatch(item -> item.startsWith("DELETE"))).isTrue();
        }
    }

    @Test
    void compatibleExistingIndexIsValidatedWithoutRecreatingIt() throws Exception {
        ElasticsearchProperties p = startServer();
        exists = true;
        try (ElasticsearchTransport transport = new ElasticsearchConfig().elasticsearchTransport(p)) {
            assertThat(new ProductSearchIndexInitializer(new ElasticsearchClient(transport), p).initialize()).isFalse();
            assertThat(requests).contains("GET /campus_products_v2/_mapping");
            assertThat(requests.stream().noneMatch(item -> item.startsWith("PUT") || item.startsWith("DELETE"))).isTrue();
        }
    }

    @Test
    void incompatibleExistingIndexAndMissingPluginAreNotOverwritten() throws Exception {
        ElasticsearchProperties p = startServer();
        exists = true;
        foreignMapping = true;
        try (ElasticsearchTransport transport = new ElasticsearchConfig().elasticsearchTransport(p)) {
            ProductSearchIndexInitializer initializer = new ProductSearchIndexInitializer(new ElasticsearchClient(transport), p);
            assertThatThrownBy(initializer::initialize).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("索引结构");
            ikAvailable = false;
            assertThatThrownBy(initializer::initialize).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("IK");
            assertThat(requests.stream().noneMatch(item -> item.startsWith("PUT") || item.startsWith("DELETE"))).isTrue();
        }
    }

    @Test
    void unreachableElasticsearchFailsWithActionableMessage() throws Exception {
        ElasticsearchProperties p = startServer();
        server.stop(0);
        p.setConnectTimeoutMillis(200);
        p.setSocketTimeoutMillis(200);
        try (ElasticsearchTransport transport = new ElasticsearchConfig().elasticsearchTransport(p)) {
            assertThatThrownBy(() -> new ProductSearchIndexInitializer(new ElasticsearchClient(transport), p).initialize())
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("连接 Elasticsearch");
        }
    }

    @Test
    void sameSchemaMetadataCannotHideDisabledSearchFieldsOrSource() throws Exception {
        ElasticsearchProperties p = startServer();
        exists = true;
        try (ElasticsearchTransport transport = new ElasticsearchConfig().elasticsearchTransport(p)) {
            ProductSearchIndexInitializer initializer = new ProductSearchIndexInitializer(new ElasticsearchClient(transport), p);
            for (String field : List.of("categoryPathIds", "priceCents", "createTime", "title", "_source")) {
                disabledField = field;
                assertThatThrownBy(initializer::initialize).as("拒绝已禁用的字段 %s", field)
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("索引结构");
            }
            assertThat(requests.stream().noneMatch(item -> item.startsWith("PUT") || item.startsWith("DELETE"))).isTrue();
        }
    }

    private ElasticsearchProperties startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            requests.add(method + " " + path);
            exchange.getResponseHeaders().set("X-Elastic-Product", "Elasticsearch");
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            int status = 200;
            Object response;
            if (path.equals("/")) {
                response = Map.of("name", "test", "cluster_name", "test", "cluster_uuid", "test",
                        "tagline", "You Know, for Search", "version", Map.of("number", "8.19.23",
                                "build_flavor", "default", "build_type", "docker", "build_hash", "test",
                                "build_date", "2026-09-30T00:00:00Z", "build_snapshot", false,
                                "lucene_version", "9.12.2", "minimum_wire_compatibility_version", "7.17.0",
                                "minimum_index_compatibility_version", "7.0.0"));
            } else if (path.equals("/_analyze")) {
                String analyzer = json.readTree(exchange.getRequestBody()).path("analyzer").asText();
                if (ikAvailable && !analyzer.equals(unavailableAnalyzer)) response = Map.of("tokens", List.of(Map.of("token", "校园", "start_offset", 0, "end_offset", 2, "position", 0, "type", "word")));
                else {
                    status = 400;
                    response = Map.of("error", Map.of("type", "illegal_argument_exception", "reason", "failed to find analyzer [ik_max_word]"), "status", 400);
                }
            } else if (method.equals("HEAD")) {
                exchange.sendResponseHeaders(exists ? 200 : 404, -1);
                exchange.close();
                return;
            } else if (method.equals("GET") && path.endsWith("/_mapping")) {
                JsonNode mapping = json.readTree(getClass().getResourceAsStream(
                        "/search/product-index-v2.json")).get("mappings");
                if (legacyMapping) ((ObjectNode) mapping.path("_meta")).put("schema_version", "1");
                if (wrongSearchAnalyzer) {
                    ((ObjectNode) mapping.path("properties").path("title")).put("search_analyzer", "standard");
                }
                if ("_source".equals(disabledField)) {
                    ((ObjectNode) mapping).putObject("_source").put("enabled", false);
                } else if (disabledField != null) {
                    ObjectNode field = (ObjectNode) mapping.path("properties").path(disabledField);
                    field.put("index", false);
                    if (!"title".equals(disabledField)) field.put("doc_values", false);
                }
                response = Map.of(path.substring(1, path.length() - "/_mapping".length()),
                        Map.of("mappings", foreignMapping ? Map.of("properties", Map.of()) : mapping));
            } else if (method.equals("PUT")) {
                createdBody = json.readTree(exchange.getRequestBody());
                exists = true;
                response = Map.of("acknowledged", true, "shards_acknowledged", true, "index", "campus_products_v2");
            } else {
                status = 404;
                response = Map.of("error", Map.of("type", "not_found", "reason", "unexpected request"), "status", 404);
            }
            byte[] body = json.writeValueAsBytes(response);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        ElasticsearchProperties properties = new ElasticsearchProperties();
        properties.setUrl("http://127.0.0.1:" + server.getAddress().getPort());
        return properties;
    }
}
