package com.secondhand.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI文档基础信息配置类
 */
@Configuration  // 标识为配置类
public class OpenApiConfig {

    /**
     * 生成OpenAPI文档元数据
     */
    @Bean  // 声明为Spring Bean
    public OpenAPI customOpenAPI() {
        // 创建并返回OpenAPI配置对象
        return new OpenAPI()
                // 设置API文档信息
                .info(new Info()
                        .title("二手交易平台接口文档")  // 文档标题
                        .version("1.0")  // 版本号
                        .description("二手交易平台后端在线接口文档。"  // 文档描述
                                + "认证基于 Spring Session 和 Redis，"
                                + "涵盖商品、订单、评价、文件上传与用户等核心能力。"));
    }
}