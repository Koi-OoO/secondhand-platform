package com.secondhand.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondhand.search.ElasticsearchProperties;
import com.secondhand.search.ProductSearchIndexInitializer;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 显式启用后注册 ES 客户端；连接池复用，关闭 Spring 容器时释放资源。 */
@Configuration
@EnableConfigurationProperties(ElasticsearchProperties.class)
@ConditionalOnProperty(prefix = "app.search.elasticsearch", name = "enabled", havingValue = "true")
public class ElasticsearchConfig {

    @Bean(destroyMethod = "close")
    public ElasticsearchTransport elasticsearchTransport(ElasticsearchProperties properties) {
        properties.validate();
        RestClientBuilder builder = RestClient.builder(HttpHost.create(properties.getUrl()))
                .setRequestConfigCallback(config -> config
                        .setConnectTimeout(properties.getConnectTimeoutMillis())
                        .setConnectionRequestTimeout(properties.getConnectTimeoutMillis())
                        .setSocketTimeout(properties.getSocketTimeoutMillis()));
        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            BasicCredentialsProvider credentials = new BasicCredentialsProvider();
            credentials.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(properties.getUsername(), properties.getPassword()));
            builder.setHttpClientConfigCallback(config -> config.setDefaultCredentialsProvider(credentials));
        }
        // ES 文档使用独立 mapper，不受 Spring MVC 全局日期格式影响，也不修改其配置。
        return new RestClientTransport(builder.build(), new JacksonJsonpMapper(new ObjectMapper()));
    }

    // 生命周期由 transport 统一负责，禁止 Spring 再推断客户端 close 导致重复释放。
    @Bean(destroyMethod = "")
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
        return new ElasticsearchClient(transport);
    }

    @Bean
    public ProductSearchIndexInitializer productSearchIndexInitializer(
            ElasticsearchClient client, ElasticsearchProperties properties) {
        return new ProductSearchIndexInitializer(client, properties);
    }
}
