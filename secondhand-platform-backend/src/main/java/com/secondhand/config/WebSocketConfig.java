package com.secondhand.config;

import com.secondhand.interceptor.WsHandshakeInterceptor;
import com.secondhand.websocket.ChatWebSocketHandler;
import com.secondhand.websocket.ProductWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置：注册私信/通知的处理器与握手拦截器。
 */
@Configuration  // 标识为配置类
@EnableWebSocket  // 开启WebSocket支持
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired  // 注入消息处理器
    private ChatWebSocketHandler chatWebSocketHandler;
    @Autowired  // 注入握手鉴权拦截器
    private WsHandshakeInterceptor wsHandshakeInterceptor;
    @Autowired
    private ProductWebSocketHandler productWebSocketHandler;

    /**
     * 注册 WebSocket 端点 /ws
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(productWebSocketHandler, "/ws/products")
                .setAllowedOriginPatterns("*");
        registry.addHandler(chatWebSocketHandler, "/ws")  // 处理器映射到 /ws
                .addInterceptors(wsHandshakeInterceptor)  // 握手阶段做登录校验
                .setAllowedOriginPatterns("*");  // 允许跨域握手（配合携带cookie）
    }
}
