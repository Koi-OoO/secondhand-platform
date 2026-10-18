package com.secondhand.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.secondhand.mapper.*;
import com.secondhand.search.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/** 同步可独立关闭；沿用现有 ES 文档转换，不侵入发布、订单和收藏业务。 */
@Configuration
@ConditionalOnProperty(prefix = "app.search.canal", name = "enabled", havingValue = "true")
@EnableConfigurationProperties({CanalSyncProperties.class, ElasticsearchProperties.class})
public class CanalSyncConfig {
    public CanalSyncConfig(CanalSyncProperties properties, ElasticsearchProperties es) { properties.validate(es); }

    @Bean public ProductSearchSyncWriter productSearchSyncWriter(ElasticsearchClient client, ElasticsearchProperties es,
            ProductMapper products, ProductImageMapper images, CategoryMapper categories, ProductSearchDocumentFactory factory) {
        return new ProductSearchSyncWriter(client, es, products, images, categories, factory);
    }

    @Bean public CanalSyncWorker canalSyncWorker(CanalSyncProperties properties, ProductSearchSyncWriter writer) {
        return new CanalSyncWorker(properties, new CanalBatchProcessor(
                new CanalChangeDecoder(properties.getSchema()), writer, properties.getBatchSize()));
    }

    @Bean @Order(30)
    public ApplicationRunner canalSyncRunner(CanalSyncWorker worker, ProductSearchIndexInitializer initializer) {
        return args -> { initializer.initialize(); worker.start(); };
    }
}
