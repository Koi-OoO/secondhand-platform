package com.secondhand.interceptor;

import com.secondhand.entity.User;
import com.secondhand.util.SessionUtil;
import com.secondhand.websocket.ChatWebSocketHandler;
import java.util.Map;
import javax.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * WebSocket 握手拦截器：复用现有登录态。
 * 握手本质是一个 HTTP 请求，会携带同一个 SESSION cookie，
 * 因此可直接从 Spring Session（Redis 托管）取出已登录用户，无需额外 token。
 */
@Component  // 注册为Spring Bean，供WebSocketConfig注入
public class WsHandshakeInterceptor implements HandshakeInterceptor {

    /**
     * 握手前校验登录，并把用户 ID 放入 attributes（供 Handler 使用）
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // 仅处理基于Servlet的请求
        if (request instanceof ServletServerHttpRequest) {
            // 取已存在的HttpSession（不新建）；Spring Session下其背后由Redis托管
            HttpSession session = ((ServletServerHttpRequest) request).getServletRequest().getSession(false);
            if (session != null) {
                // 复用SessionUtil取登录用户（与普通接口同一套登录态）
                User user = SessionUtil.getUser(session);
                if (user != null) {
                    // 把用户ID存入握手attributes，连接建立后Handler可读取
                    attributes.put(ChatWebSocketHandler.USER_ID_KEY, user.getId());
                    return true;  // 放行握手
                }
            }
        }
        // 未登录：拒绝握手并返回401
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }

    /**
     * 握手后无需额外处理
     */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 空实现
    }
}
