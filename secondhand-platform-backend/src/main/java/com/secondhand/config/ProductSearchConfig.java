package com.secondhand.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.secondhand.mapper.CategoryMapper;
import com.secondhand.mapper.ProductImageMapper;
import com.secondhand.mapper.ProductMapper;
import com.secondhand.search.ElasticsearchProperties;
import com.secondhand.search.ProductSearchDocumentFactory;
import com.secondhand.search.ProductSearchImportService;
import com.secondhand.search.ProductSearchIndexInitializer;
import com.secondhand.search.ProductSearchService;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/** 商品检索与导入单独装配，连接配置不依赖业务 mapper。 */
@Configuration
@ConditionalOnProperty(prefix = "app.search.elasticsearch", name = "enabled", havingValue = "true")
public class ProductSearchConfig {
    @Bean
    public ProductSearchDocumentFactory productSearchDocumentFactory() {
        return new ProductSearchDocumentFactory();
    }

    @Bean
    public ProductSearchImportService productSearchImportService(ElasticsearchClient client, ElasticsearchProperties p,
            ProductMapper products, ProductImageMapper images, CategoryMapper categories,
            ProductSearchDocumentFactory factory, ProductSearchIndexInitializer initializer) {
        return new ProductSearchImportService(client, p, products, images, categories, factory, initializer);
    }

    @Bean
    public ProductSearchService productSearchService(ElasticsearchClient client, ElasticsearchProperties p,
            ProductMapper products, ProductImageMapper images, CategoryMapper categories, ProductSearchDocumentFactory factory) {
        return new ProductSearchService(client, p, products, images, categories, factory);
    }

    @Bean
    @Order(20)
    @ConditionalOnProperty(prefix = "app.search.elasticsearch", name = "import-on-startup", havingValue = "true")
    public ApplicationRunner productSearchImportRunner(ProductSearchImportService importer) {
        return args -> LoggerFactory.getLogger(ProductSearchImportService.class)
                .info("商品搜索导入完成：共 {} 条；请关闭 import-on-startup 避免每次启动重复全量导入", importer.importAll());
    }
}
