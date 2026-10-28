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
import org.slf4j.Logger;
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
    private static final Logger log = LoggerFactory.getLogger(ProductSearchConfig.class);

    @Bean
    public ProductSearchDocumentFactory productSearchDocumentFactory() {
        return new ProductSearchDocumentFactory();
    }

    @Bean
    public ProductSearchImportService productSearchImportService(ElasticsearchClient client,
            ElasticsearchProperties properties, ProductMapper productMapper, ProductImageMapper productImageMapper,
            CategoryMapper categoryMapper, ProductSearchDocumentFactory documentFactory,
            ProductSearchIndexInitializer indexInitializer) {
        return new ProductSearchImportService(client, properties, productMapper, productImageMapper,
                categoryMapper, documentFactory, indexInitializer);
    }

    @Bean
    public ProductSearchService productSearchService(ElasticsearchClient client, ElasticsearchProperties properties,
            ProductMapper productMapper, ProductImageMapper productImageMapper, CategoryMapper categoryMapper,
            ProductSearchDocumentFactory documentFactory) {
        return new ProductSearchService(client, properties, productMapper, productImageMapper,
                categoryMapper, documentFactory);
    }

    @Bean
    @Order(20)
    @ConditionalOnProperty(prefix = "app.search.elasticsearch", name = "import-on-startup", havingValue = "true")
    public ApplicationRunner productSearchImportRunner(ProductSearchImportService importService) {
        return args -> {
            long imported = importService.importAll();
            log.info("商品搜索导入完成：共 {} 条；请关闭 import-on-startup 避免每次启动重复全量导入", imported);
        };
    }
}
