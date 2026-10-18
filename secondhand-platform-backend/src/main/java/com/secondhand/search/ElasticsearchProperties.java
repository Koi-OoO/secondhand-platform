package com.secondhand.search;

import java.net.URI;
import java.net.URISyntaxException;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 商品搜索的 ES 连接配置；默认关闭，避免传统业务依赖本地 ES 是否启动。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.search.elasticsearch")
public class ElasticsearchProperties {
    private boolean enabled;
    private boolean importOnStartup;
    private String url = "http://127.0.0.1:9200";
    private String indexName = "campus_products_v2";
    private int connectTimeoutMillis = 1000;
    private int socketTimeoutMillis = 5000;
    private String username = "";
    private String password = "";

    /** 启用客户端前校验配置；异常不回显可能包含凭据的原始 URL 或密码。 */
    public void validate() {
        try {
            URI endpoint = new URI(url == null ? "" : url);
            String path = endpoint.getRawPath();
            if (!("http".equals(endpoint.getScheme()) || "https".equals(endpoint.getScheme()))
                    || endpoint.getHost() == null || endpoint.getRawUserInfo() != null
                    || endpoint.getRawQuery() != null || endpoint.getRawFragment() != null
                    || (path != null && !path.isEmpty() && !"/".equals(path))
                    || endpoint.getPort() == 0 || endpoint.getPort() > 65535) {
                throw new IllegalArgumentException("ES 地址必须是 HTTP(S) 主机地址，不允许内嵌凭据、路径或查询参数");
            }
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("ES 地址格式无效");
        }
        if (indexName == null || indexName.length() > 255 || !indexName.matches("[a-z0-9][a-z0-9_.-]*")) {
            throw new IllegalArgumentException("商品索引名需以小写字母或数字开头，只包含小写字母、数字、下划线、点或连字符");
        }
        if (connectTimeoutMillis <= 0 || socketTimeoutMillis <= 0) {
            throw new IllegalArgumentException("ES 连接和读取超时必须大于 0");
        }
        if (hasText(username) != hasText(password)) {
            throw new IllegalArgumentException("ES 用户名和密码必须同时配置或同时留空");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
