package com.secondhand.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.secondhand.mapper.CategoryMapper;
import com.secondhand.mapper.ProductImageMapper;
import com.secondhand.mapper.ProductMapper;
import com.secondhand.search.CanalBatchProcessor;
import com.secondhand.search.CanalChangeDecoder;
import com.secondhand.search.CanalSyncProperties;
import com.secondhand.search.CanalSyncWorker;
import com.secondhand.search.ElasticsearchProperties;
import com.secondhand.search.ProductSearchDocumentFactory;
import com.secondhand.search.ProductSearchIndexInitializer;
import com.secondhand.search.ProductSearchSyncWriter;
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

    public CanalSyncConfig(CanalSyncProperties properties, ElasticsearchProperties elasticsearchProperties) {
        properties.validate(elasticsearchProperties);
    }

    @Bean
    public ProductSearchSyncWriter productSearchSyncWriter(ElasticsearchClient client,
            ElasticsearchProperties properties, ProductMapper productMapper, ProductImageMapper productImageMapper,
            CategoryMapper categoryMapper, ProductSearchDocumentFactory documentFactory) {
        return new ProductSearchSyncWriter(client, properties, productMapper, productImageMapper,
                categoryMapper, documentFactory);
    }

    @Bean
    public CanalSyncWorker canalSyncWorker(CanalSyncProperties properties, ProductSearchSyncWriter syncWriter) {
        CanalBatchProcessor batchProcessor = new CanalBatchProcessor(
                new CanalChangeDecoder(properties.getSchema()), syncWriter, properties.getBatchSize());
        return new CanalSyncWorker(properties, batchProcessor);
    }

    @Bean
    @Order(30)
    public ApplicationRunner canalSyncRunner(CanalSyncWorker worker, ProductSearchIndexInitializer initializer) {
        return args -> {
            // 先校验索引，再启动 Canal 消费，避免写入不存在的索引
            initializer.initialize();
            worker.start();
        };
    }
}
