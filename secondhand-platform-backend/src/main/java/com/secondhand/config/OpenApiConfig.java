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
                                + "涵盖商品、订单、评价、收藏、私信、通知、文件上传与用户等核心能力。"
                                + "前端开发环境通过 /api 代理到后端，后端真实接口路径不带 /api 前缀。"
                                + "私信模块中 POST /message/conversation 用于点击联系卖家时创建持久空会话，"
                                + "GET /message/online-status 返回 online 与 lastActiveTime。"
                                + "私信与通知的实时推送基于 WebSocket：登录后连接 ws://{host}/ws"
                                + "（握手复用登录态，无需额外 token），服务端下推 JSON 文本 "
                                + "{\"type\":\"chat|notification\",\"data\":{...}}；"
                                + "客户端可发送 ping 保活，服务端回 pong。"));
    }
}
