package com.secondhand.websocket;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 私信 / 通知的 WebSocket 处理器。
 * 维护「用户 ID -> 在线连接」的映射，负责把消息和通知实时推给在线用户。
 * 设计上 WebSocket 只做下行推送，所有读写仍走 REST 接口。
 */
@Component  // 注册为Spring Bean，供配置类与业务层注入
public class ChatWebSocketHandler extends TextWebSocketHandler {

    // 日志记录器
    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    // 握手阶段存入attributes的用户ID key（与WsHandshakeInterceptor约定一致）
    public static final String USER_ID_KEY = "userId";

    // 在线连接表：一个用户可能多标签页/多端登录，故value用线程安全的Set
    private final ConcurrentHashMap<Long, Set<WebSocketSession>> onlineSessions = new ConcurrentHashMap<>();

    /**
     * 连接建立：把当前连接登记到在线表
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 取握手时存入的用户ID
        Long userId = currentUserId(session);
        if (userId == null) {
            return;  // 理论上不会发生（握手拦截器已校验登录）
        }
        // computeIfAbsent并发安全地初始化集合，再把当前连接加入
        onlineSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.<WebSocketSession>newKeySet()).add(session);
        log.info("WebSocket连接建立：userId={} 该用户在线连接数={}", userId, onlineSessions.get(userId).size());
    }

    /**
     * 连接关闭：从在线表移除该连接，集合空了顺带清理key，避免内存泄漏
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // 取握手时存入的用户ID
        Long userId = currentUserId(session);
        if (userId == null) {
            return;
        }
        // 取出该用户的连接集合
        Set<WebSocketSession> sessions = onlineSessions.get(userId);
        if (sessions != null) {
            sessions.remove(session);  // 移除当前关闭的连接
            if (sessions.isEmpty()) {
                onlineSessions.remove(userId);  // 该用户已全部离线，清理key
            }
        }
        log.info("WebSocket连接关闭：userId={}", userId);
    }

    /**
     * 处理上行消息：仅做心跳保活（发消息走REST），收到ping回pong
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        // 客户端定时发送ping用于保活
        if ("ping".equals(message.getPayload())) {
            // WebSocketSession非线程安全，发送需串行化
            synchronized (session) {
                session.sendMessage(new TextMessage("pong"));  // 心跳应答
            }
        }
    }

    /**
     * 向指定用户推送一条文本（JSON）。
     * 在线才推，离线直接跳过（消息已落库，下次拉取可见）；
     * 推送失败仅记日志、不抛出，保证 best-effort，不影响业务主流程。
     *
     * @param userId 接收者用户ID
     * @param json   已序列化好的JSON字符串
     */
    public void sendToUser(Long userId, String json) {
        if (userId == null) {
            return;
        }
        // 取出该用户的在线连接
        Set<WebSocketSession> sessions = onlineSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;  // 用户不在线，跳过推送
        }
        // 遍历该用户的所有在线连接逐个推送
        for (WebSocketSession session : sessions) {
            try {
                // WebSocketSession非线程安全，对同一连接的发送需串行化
                synchronized (session) {
                    if (session.isOpen()) {  // 仅向仍打开的连接发送
                        session.sendMessage(new TextMessage(json));
                    }
                }
            } catch (Exception ex) {
                // 推送失败仅记日志，不影响业务（best-effort）
                log.warn("WebSocket推送失败：userId={}", userId, ex);
            }
        }
    }

    /**
     * 判断用户当前是否在线
     */
    public boolean isOnline(Long userId) {
        Set<WebSocketSession> sessions = onlineSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    /**
     * 从连接的握手attributes中取出用户ID
     */
    private Long currentUserId(WebSocketSession session) {
        Object userId = session.getAttributes().get(USER_ID_KEY);
        return userId instanceof Long ? (Long) userId : null;
    }
}
