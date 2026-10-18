package com.secondhand;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 后端应用启动入口。
 */
// ES 客户端由 ElasticsearchConfig 显式管理，避免引入依赖后 Boot 自动建立额外连接池。
@SpringBootApplication(exclude = ElasticsearchRestClientAutoConfiguration.class)
@EnableScheduling
public class SecondhandPlatformApplication {

    /**
     * 启动 Spring Boot 后端服务，并加载定时任务与相关基础设施。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SecondhandPlatformApplication.class, args);
    }
}
