package com.secondhand.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondhand.SecondhandPlatformApplication;
import com.secondhand.entity.Product;
import com.secondhand.entity.ProductImage;
import com.secondhand.mapper.ProductMapper;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.*;

/** 显式开启才读取本机数据库；只写随机 ES 索引，不向 MySQL 插入、修改或删除数据。 */
@EnabledIfEnvironmentVariable(named = "CAMPUS_ES_PRODUCT_SEARCH_LIVE", matches = "true")
class ProductSearchLiveTest {
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @Test
    @Timeout(120)
    void realImportSearchImagesPaginationAndHttpErrors() throws Exception {
        String endpoint = System.getenv("CAMPUS_ES_URL");
        assertThat(URI.create(endpoint).getHost()).isIn("127.0.0.1", "localhost", "::1", "[::1]");
        String index = "campus_products_read_test_" + UUID.randomUUID().toString().replace("-", "");
        try (ConfigurableApplicationContext context = application(true, endpoint, index, true)) {
            ElasticsearchClient client = context.getBean(ElasticsearchClient.class);
            try {
                ProductMapper mapper = context.getBean(ProductMapper.class);
                List<Product> database = mapper.selectList(null);
                assertThat(database).as("本机应已有演示商品").isNotEmpty();
                ProductSearchImportService importer = context.getBean(ProductSearchImportService.class);
                // 验证显式启动导入开关已在服务就绪前完成导入。
                assertThat(client.count(count -> count.index(index)).count()).isEqualTo(database.size());
                assertThat(importer.importAll()).isEqualTo(database.size());
                assertThat(client.count(count -> count.index(index)).count()).isEqualTo(database.size());

                List<Product> available = database.stream().filter(p -> Integer.valueOf(1).equals(p.getStatus()))
                        .sorted(Comparator.comparing(Product::getPrice).thenComparing(Product::getId, Comparator.reverseOrder()))
                        .collect(Collectors.toList());
                assertThat(available.size()).isGreaterThan(50);
                String base = base(context);
                List<Long> pageIds = new ArrayList<>();
                for (int page = 1; page <= 2; page++) {
                    JsonNode result = get(base + "/product/search?size=50&sort=price_asc&page=" + page, 200);
                    assertThat(result.path("data").path("total").asLong()).isEqualTo(available.size());
                    for (JsonNode product : result.path("data").path("records")) pageIds.add(product.path("id").asLong());
                }
                assertThat(pageIds).containsExactlyElementsOf(available.stream().limit(100).map(Product::getId).collect(Collectors.toList()));
                assertThat(pageIds).doesNotHaveDuplicates();

                Product example = available.stream().filter(p -> p.getCategoryId() != null && p.getTitle() != null)
                        .filter(p -> !p.getTitle().isBlank()).findFirst().orElseThrow();
                var tokens = client.indices().analyze(analyze -> analyze.analyzer("ik_smart").text(example.getTitle())).tokens();
                assertThat(tokens).isNotEmpty();
                String keyword = tokens.stream().limit(2).map(token -> token.token()).collect(Collectors.joining(" "));
                String query = "/product/search?keyword=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
                        + "&categoryId=" + example.getCategoryId() + "&minPrice=" + example.getPrice().toPlainString()
                        + "&maxPrice=" + example.getPrice().toPlainString() + "&size=50";
                JsonNode found = get(base + query, 200);
                List<Long> foundIds = new ArrayList<>();
                JsonNode card = null;
                for (JsonNode record : found.path("data").path("records")) {
                    foundIds.add(record.path("id").asLong());
                    if (record.path("id").asLong() == example.getId()) card = record;
                }
                assertThat(foundIds).contains(example.getId());
                assertThat(card.path("price").decimalValue()).isEqualByComparingTo(example.getPrice());
                List<ProductImage> pictures = context.getBean(com.secondhand.mapper.ProductImageMapper.class)
                        .selectByProductId(example.getId());
                assertThat(pictures).as("验证商品须有真实图片").isNotEmpty();
                assertThat(card.path("images").size()).isEqualTo(pictures.size());
                assertThat(card.path("images").get(0).path("url").asText()).isNotBlank();
                assertThat(client.get(get -> get.index(index).id(example.getId().toString()), java.util.Map.class)
                        .source().get("priceCents").toString()).isEqualTo(example.getPrice().movePointRight(2).toBigIntegerExact().toString());
                get(base + "/product/search?size=51", 400);
                get(base + "/product/search?page=abc", 400);
                get(base + "/product/search?minPrice=100&maxPrice=1", 400);
                // 旧接口仍查询 MySQL，且不会因新增接口而改变。
                assertThat(get(base + "/product/page?size=20", 200).path("code").asInt()).isEqualTo(200);

                RestClientTransport transport = (RestClientTransport) client._transport();
                int closedPort;
                try (ServerSocket socket = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
                    closedPort = socket.getLocalPort();
                }
                // 仅改变本次测试客户端的目标，模拟 ES 断开；不停止用户的 ES 容器。
                transport.restClient().setNodes(List.of(new Node(new HttpHost("127.0.0.1", closedPort, "http"))));
                try {
                    get(base + "/product/search", 503);
                    assertThat(get(base + "/product/page?size=20", 200).path("code").asInt()).isEqualTo(200);
                } finally {
                    transport.restClient().setNodes(List.of(new Node(HttpHost.create(endpoint))));
                }
                assertThat(mapper.selectCount(null)).isEqualTo(database.size());
                System.out.println("真实搜索联调通过：导入 " + database.size() + " 条，重复导入不增量，中文/预算/分类/图片/分页/400/503及旧接口均验证。");
            } finally {
                if (index.startsWith("campus_products_read_test_") && client.indices().exists(exists -> exists.index(index)).value()) {
                    client.indices().delete(delete -> delete.index(index));
                }
            }
        }
    }

    @Test
    @Timeout(90)
    void actualDisabledApplicationReturns503AndKeepsTraditionalPageWorking() throws Exception {
        try (ConfigurableApplicationContext context = application(false, "http://127.0.0.1:1", "unused_products_test", false)) {
            assertThat(context.getBeansOfType(ElasticsearchClient.class)).isEmpty();
            get(base(context) + "/product/search", 503);
            assertThat(get(base(context) + "/product/page?size=20", 200).path("code").asInt()).isEqualTo(200);
        }
    }

    /** 只有再次显式允许时才写正式索引；不会删除该索引或改动 MySQL。 */
    @Test
    @Timeout(90)
    @EnabledIfEnvironmentVariable(named = "CAMPUS_ES_IMPORT_FORMAL", matches = "true")
    void explicitlyInitializeFormalIndexFromCurrentDatabase() throws Exception {
        String endpoint = System.getenv("CAMPUS_ES_URL");
        assertThat(URI.create(endpoint).getHost()).isIn("localhost", "127.0.0.1", "::1", "[::1]");
        try (ConfigurableApplicationContext context = application(true, endpoint, "campus_products_v2", true)) {
            long expected = context.getBean(ProductMapper.class).selectCount(null);
            long actual = context.getBean(ElasticsearchClient.class).count(count -> count.index("campus_products_v2")).count();
            assertThat(actual).isEqualTo(expected);
            assertThat(get(base(context) + "/product/search?size=20", 200).path("code").asInt()).isEqualTo(200);
            System.out.println("正式 campus_products_v2 导入并通过 HTTP 查询验证：" + actual + " 条；未修改 MySQL。");
        }
    }

    private ConfigurableApplicationContext application(boolean enabled, String endpoint, String index, boolean importOnStartup) {
        return new SpringApplicationBuilder(SecondhandPlatformApplication.class).web(WebApplicationType.SERVLET)
                .run("--server.address=127.0.0.1", "--server.port=0", "--spring.main.banner-mode=off",
                        "--app.search.elasticsearch.enabled=" + enabled, "--app.search.elasticsearch.url=" + endpoint,
                        "--app.search.elasticsearch.index-name=" + index, "--app.search.elasticsearch.import-on-startup=" + importOnStartup,
                        "--mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl",
                        "--app.upload.local-path=" + Path.of("target", "es-read-live-upload").toAbsolutePath(),
                        "--logging.level.root=WARN");
    }

    private String base(ConfigurableApplicationContext context) {
        return "http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port");
    }

    private JsonNode get(String url, int expectedStatus) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(response.statusCode()).as("HTTP %s", url).isEqualTo(expectedStatus);
        return json.readTree(response.body());
    }
}
