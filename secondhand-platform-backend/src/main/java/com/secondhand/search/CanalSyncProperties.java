package com.secondhand.search;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Java 到 Canal 的连接配置；这里的凭据不是 Canal 到 MySQL 的账号密码。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.search.canal")
public class CanalSyncProperties {
    private boolean enabled;
    private String host = "127.0.0.1";
    private int port = 11111;
    private String destination = "campus";
    private String schema = "secondhand";
    private String username = "";
    private String password = "";
    private int batchSize = 100;
    private int socketTimeoutMillis = 10000;
    private int retryDelayMillis = 5000;
    private int idleDelayMillis = 500;

    public void validate(ElasticsearchProperties es) {
        // 1. Canal 同步依赖 ES 搜索，且不能与启动时全量导入同时启用
        if (!es.isEnabled()) {
            throw new IllegalArgumentException("启用 Canal 同步必须同时启用 ES 搜索");
        }
        if (es.isImportOnStartup()) {
            throw new IllegalArgumentException("全量导入与 Canal 消费不可同时启用，请按初始化步骤分阶段启动");
        }

        // 2. 校验连接地址、destination、schema 和轮询参数
        if (host == null || host.isBlank()
                || port < 1 || port > 65535
                || destination == null || !destination.matches("[A-Za-z0-9_-]+")
                || schema == null || !schema.matches("[A-Za-z0-9_]+")
                || batchSize < 1 || batchSize > 1000
                || socketTimeoutMillis < 1000 || socketTimeoutMillis > 60000
                || retryDelayMillis < 1000 || retryDelayMillis > 60000
                || idleDelayMillis < 100 || idleDelayMillis > 10000) {
            throw new IllegalArgumentException("Canal 连接或轮询配置无效");
        }

        // 3. 客户端用户名和密码必须成对配置
        if ((username == null || username.isBlank()) != (password == null || password.isBlank())) {
            throw new IllegalArgumentException("Canal 客户端用户名和密码必须同时配置或同时留空");
        }
    }

    /** 订阅白名单；库名已校验，不允许通过正则扩大订阅范围。 */
    public String filter() {
        return schema + "\\.(product|product_image|category)";
    }
}
