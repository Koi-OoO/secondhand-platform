package com.secondhand;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 后端应用启动入口。
 */
@SpringBootApplication
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
