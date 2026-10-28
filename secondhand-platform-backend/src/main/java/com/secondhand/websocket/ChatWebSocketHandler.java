package com.secondhand.websocket;

import java.io.IOException;
import java.util.Date;
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
    public static final String HTTP_SESSION_ID_KEY = "httpSessionId";

    // 在线连接表：一个用户可能多标签页/多端登录，故value用线程安全的Set
    private final ConcurrentHashMap<Long, Set<WebSocketSession>> onlineSessions = new ConcurrentHashMap<>();

    // 最后活跃时间：连接建立、心跳、最后一个连接关闭时更新，用于前端展示「刚刚在线」
    private final ConcurrentHashMap<Long, Long> lastActiveTimes = new ConcurrentHashMap<>();
    // 记录已撤销的会话ID，拒绝那些在切换前开始握手、切换后才完成登记的旧连接。
    private final ConcurrentHashMap<String, Long> retiredHttpSessions = new ConcurrentHashMap<>();

    public void closeSessionConnections(String httpSessionId) {
        // 1. 没有会话ID时无需处理
        if (httpSessionId == null) {
            return;
        }

        // 2. 记录已撤销的会话，保留7天，拒绝迟到的旧连接
        long now = System.currentTimeMillis();
        retiredHttpSessions.entrySet().removeIf(entry -> entry.getValue() < now);
        retiredHttpSessions.put(httpSessionId, now + 7 * 86400_000L);

        // 3. 遍历在线连接，关闭属于该会话的连接
        onlineSessions.values().forEach(sessions -> sessions.forEach(session -> {
            if (httpSessionId.equals(session.getAttributes().get(HTTP_SESSION_ID_KEY))) {
                try {
                    synchronized (session) {
                        session.close(CloseStatus.NORMAL);
                    }
                } catch (IOException exception) {
                    log.warn("WebSocket会话关闭失败", exception);
                } finally {
                    afterConnectionClosed(session, CloseStatus.NORMAL);
                }
            }
        }));
    }

    /**
     * 连接建立：把当前连接登记到在线表
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 1. 握手阶段已被撤销的会话直接关闭
        Object httpSessionId = session.getAttributes().get(HTTP_SESSION_ID_KEY);
        if (httpSessionId instanceof String && retiredHttpSessions.containsKey(httpSessionId)) {
            try {
                session.close(CloseStatus.NORMAL);
            } catch (IOException exception) {
                log.warn("WebSocket会话关闭失败", exception);
            }
            return;
        }

        // 2. 取握手时存入的用户ID
        Long userId = currentUserId(session);
        if (userId == null) {
            return;  // 理论上不会发生（握手拦截器已校验登录）
        }

        // 3. 并发安全地初始化在线集合，再把当前连接加入
        onlineSessions.computeIfAbsent(userId, userIdKey -> ConcurrentHashMap.<WebSocketSession>newKeySet()).add(session);

        // 4. 登记后再检查一次，覆盖第一次检查与登记之间恰好发生账号切换的情况
        if (httpSessionId instanceof String && retiredHttpSessions.containsKey(httpSessionId)) {
            try {
                session.close(CloseStatus.NORMAL);
            } catch (IOException exception) {
                log.warn("WebSocket会话关闭失败", exception);
            } finally {
                afterConnectionClosed(session, CloseStatus.NORMAL);
            }
            return;
        }

        // 5. 更新最后活跃时间并记录连接数
        touchUser(userId);
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
                touchUser(userId);
            }
        } else {
            touchUser(userId);
        }
        log.info("WebSocket连接关闭：userId={}", userId);
    }

    /**
     * 处理上行消息：仅做心跳保活（发消息走REST），收到ping回pong
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        // 1. 只处理客户端定时发送的 ping
        if ("ping".equals(message.getPayload())) {
            // 2. 更新最后活跃时间
            Long userId = currentUserId(session);
            if (userId != null) {
                touchUser(userId);
            }

            // 3. WebSocketSession非线程安全，串行回复 pong
            synchronized (session) {
                session.sendMessage(new TextMessage("pong"));
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
        // 1. 没有接收者时直接跳过
        if (userId == null) {
            return;
        }

        // 2. 取出该用户的在线连接，离线直接跳过（消息已落库，下次拉取可见）
        Set<WebSocketSession> sessions = onlineSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        // 3. 逐个在线连接推送，单个连接失败不影响其他连接
        for (WebSocketSession session : sessions) {
            try {
                // WebSocketSession非线程安全，对同一连接的发送需串行化
                synchronized (session) {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(json));
                    }
                }
            } catch (Exception exception) {
                // 推送失败仅记日志，不影响业务（best-effort）
                log.warn("WebSocket推送失败：userId={}", userId, exception);
            }
        }
    }

    /**
     * 判断用户当前是否在线
     */
    public boolean isOnline(Long userId) {
        // 1. 没有连接记录时直接视为离线
        Set<WebSocketSession> sessions = onlineSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return false;
        }

        // 2. 清理已经关闭的连接
        sessions.removeIf(session -> !session.isOpen());
        if (sessions.isEmpty()) {
            onlineSessions.remove(userId);
            touchUser(userId);
            return false;
        }

        // 3. 仍有打开的连接则在线
        return true;
    }

    /**
     * 查询用户最后活跃时间，用于前端展示「刚刚在线 / 10分钟前在线」。
     */
    public Date getLastActiveTime(Long userId) {
        Long timestamp = lastActiveTimes.get(userId);
        return timestamp == null ? null : new Date(timestamp);
    }

    /**
     * 从连接的握手attributes中取出用户ID
     */
    private Long currentUserId(WebSocketSession session) {
        Object userId = session.getAttributes().get(USER_ID_KEY);
        return userId instanceof Long ? (Long) userId : null;
    }

    private void touchUser(Long userId) {
        if (userId != null) {
            lastActiveTimes.put(userId, System.currentTimeMillis());
        }
    }
}
