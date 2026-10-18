package com.secondhand.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.get_mapping.IndexMappingRecord;
import co.elastic.clients.json.JsonpUtils;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;

/**
 * 启用 ES 后检查连接、中文分词与商品索引。
 * 只创建缺失索引；已有结构不兼容时明确失败，绝不覆盖、删除索引或导入商品。
 */
@Order(10)
public class ProductSearchIndexInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ProductSearchIndexInitializer.class);
    private final ElasticsearchClient client;
    private final ElasticsearchProperties properties;

    public ProductSearchIndexInitializer(ElasticsearchClient client, ElasticsearchProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean created = initialize();
        log.info("商品搜索索引 {}：{}", properties.getIndexName(), created ? "已创建空索引" : "已验证现有结构");
    }

    /** @return true 表示本次创建，false 表示已有且兼容。 */
    public boolean initialize() {
        properties.validate();
        try {
            String version = client.info().version().number();
            String[] parts = version.split("\\.");
            if (parts.length < 2 || !"8".equals(parts[0]) || Integer.parseInt(parts[1]) < 19) {
                throw new IllegalStateException("当前商品搜索客户端要求 Elasticsearch 8.19 或更高的 8.x 版本");
            }
            verifyChineseAnalysis();
            CreateIndexRequest request = loadDefinition();
            if (client.indices().exists(builder -> builder.index(properties.getIndexName())).value()) {
                verifyExistingMapping(request.mappings());
                return false;
            }
            try {
                if (!client.indices().create(request).acknowledged()) {
                    throw new IllegalStateException("商品索引创建未获得确认，请检查 Elasticsearch 状态");
                }
                return true;
            } catch (ElasticsearchException ex) {
                // 多个应用实例同时启动时，另一个实例可能刚创建完，验证后复用即可。
                if (!"resource_already_exists_exception".equals(ex.error().type())) throw ex;
                verifyExistingMapping(request.mappings());
                return false;
            }
        } catch (IOException ex) {
            throw new IllegalStateException("无法连接 Elasticsearch，请检查 ES 服务、地址和超时配置", ex);
        }
    }

    private void verifyChineseAnalysis() throws IOException {
        try {
            client.indices().analyze(builder -> builder.analyzer("ik_max_word").text("校园商品搜索"));
            client.indices().analyze(builder -> builder.analyzer("ik_smart").text("校园商品搜索"));
        } catch (ElasticsearchException ex) {
            throw new IllegalStateException("无法使用 IK 中文分词，请检查匹配 ES 版本的插件是否已安装并重启，以及访问权限", ex);
        }
    }

    private CreateIndexRequest loadDefinition() throws IOException {
        String definition = "/search/product-index-v2.json";
        try (InputStream input = getClass().getResourceAsStream(definition)) {
            if (input == null) throw new IllegalStateException("商品索引定义文件缺失");
            return new CreateIndexRequest.Builder().index(properties.getIndexName()).withJson(input).build();
        }
    }

    private void verifyExistingMapping(TypeMapping expected) throws IOException {
        IndexMappingRecord record = client.indices().getMapping(builder -> builder.index(properties.getIndexName()))
                .result().get(properties.getIndexName());
        TypeMapping actual = record == null ? null : record.mappings();
        if (actual == null || actual.dynamic() != expected.dynamic()
                || Boolean.FALSE.equals(actual.enabled())
                || (actual.source() != null && (Boolean.FALSE.equals(actual.source().enabled())
                    || !actual.source().includes().isEmpty() || !actual.source().excludes().isEmpty()))
                || actual.properties().size() != expected.properties().size()
                || !expected.meta().entrySet().stream().allMatch(entry -> actual.meta().containsKey(entry.getKey())
                    && Objects.equals(actual.meta().get(entry.getKey()).to(String.class), entry.getValue().to(String.class)))) {
            throw incompatibleIndex();
        }
        ObjectMapper json = new ObjectMapper();
        JacksonJsonpMapper mapper = new JacksonJsonpMapper(json);
        for (Map.Entry<String, Property> field : expected.properties().entrySet()) {
            Property wanted = field.getValue();
            Property present = actual.properties().get(field.getKey());
            if (present == null || present._kind() != wanted._kind()) throw incompatibleIndex();
            if (wanted.isText() && !Objects.equals(present.text().analyzer(), wanted.text().analyzer())) throw incompatibleIndex();
            if (wanted.isText()) {
                // 查询分词器省略时默认沿用写入分词器，不能把 ik_max_word 误当成 ik_smart。
                String expectedSearch = wanted.text().searchAnalyzer() == null ? wanted.text().analyzer() : wanted.text().searchAnalyzer();
                String actualSearch = present.text().searchAnalyzer() == null ? present.text().analyzer() : present.text().searchAnalyzer();
                if (!Objects.equals(actualSearch, expectedSearch)) throw incompatibleIndex();
            }
            if (wanted.isDate() && !Objects.equals(present.date().format(), wanted.date().format())) throw incompatibleIndex();
            // ES 返回的映射可能省略默认值；先归一化，再比较查询和排序依赖的开关。
            JsonNode wantedJson = json.readTree(JsonpUtils.toJsonString(wanted, mapper));
            JsonNode presentJson = json.readTree(JsonpUtils.toJsonString(present, mapper));
            if (presentJson.path("index").asBoolean(true) != wantedJson.path("index").asBoolean(true)) {
                throw incompatibleIndex();
            }
            if (!wanted.isText()
                    && presentJson.path("doc_values").asBoolean(true) != wantedJson.path("doc_values").asBoolean(true)) {
                throw incompatibleIndex();
            }
        }
    }

    private IllegalStateException incompatibleIndex() {
        return new IllegalStateException("商品索引结构与当前版本不兼容；请使用新索引名并规划重建，系统不会覆盖已有数据");
    }
}
