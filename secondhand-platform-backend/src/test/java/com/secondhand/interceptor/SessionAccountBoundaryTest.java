package com.secondhand.interceptor;

import com.secondhand.service.impl.SessionAccountServiceImpl;
import com.secondhand.entity.User;
import com.secondhand.service.SessionAccountService;
import com.secondhand.service.UserService;
import com.secondhand.util.PasswordUtil;
import com.secondhand.util.SessionUtil;
import com.secondhand.websocket.ChatWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.socket.WebSocketSession;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionAccountBoundaryTest {
    @Test void handshakeNeverPairsOldUserWithAnIdRotatedDuringValidation() {
        WsHandshakeInterceptor interceptor = new WsHandshakeInterceptor();
        SessionAccountService accounts = mock(SessionAccountService.class);
        ReflectionTestUtils.setField(interceptor, "sessionAccountService", accounts);
        MockHttpServletRequest request = new MockHttpServletRequest();
        String oldId = request.getSession().getId();
        User previous = new User(); previous.setId(1L);
        when(accounts.validateCurrent(request.getSession())).thenAnswer(invocation -> {
            request.changeSessionId();
            return previous;
        });
        Map<String, Object> attributes = new HashMap<>();
        assertThat(interceptor.beforeHandshake(new org.springframework.http.server.ServletServerHttpRequest(request),
                new org.springframework.http.server.ServletServerHttpResponse(new MockHttpServletResponse()), new ChatWebSocketHandler(), attributes)).isTrue();
        assertThat(request.getSession().getId()).isNotEqualTo(oldId);
        assertThat(attributes.get(ChatWebSocketHandler.HTTP_SESSION_ID_KEY)).isEqualTo(oldId);
    }
    @Test void oldTabCannotReconnectToNewAccountsPrivateSocket() {
        WsHandshakeInterceptor interceptor = new WsHandshakeInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        User active = new User(); active.setId(2L);
        SessionUtil.login(request.getSession(), active);
        request.setParameter("activeUserId", "1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, Object> attributes = new HashMap<>();
        assertThat(interceptor.beforeHandshake(new org.springframework.http.server.ServletServerHttpRequest(request),
                new org.springframework.http.server.ServletServerHttpResponse(response), new ChatWebSocketHandler(), attributes)).isFalse();
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(attributes).isEmpty();
        request.setParameter("activeUserId", "2");
        assertThat(interceptor.beforeHandshake(new org.springframework.http.server.ServletServerHttpRequest(request),
                new org.springframework.http.server.ServletServerHttpResponse(new MockHttpServletResponse()), new ChatWebSocketHandler(), attributes)).isTrue();
        assertThat(attributes.get(ChatWebSocketHandler.USER_ID_KEY)).isEqualTo(2L);
    }
    @Test void staleIdentityStopsBusinessAndValidatedIdentityIsFrozenForRequest() throws Exception {
        UserService users = mock(UserService.class);
        User a = new User(); a.setId(1L); a.setStatus(1); a.setPassword(PasswordUtil.encrypt("a"));
        when(users.getUserById(1L)).thenReturn(a);
        SessionAccountService accounts = new SessionAccountServiceImpl(users, new ChatWebSocketHandler());
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/product/publish"); req.setSession(new MockHttpSession());
        accounts.establishLogin(req, a);
        AuthInterceptor filter = new AuthInterceptor(); ReflectionTestUtils.setField(filter, "sessionAccountService", accounts);
        req.addHeader("X-Active-User-Id", "2");
        MockHttpServletResponse response = new MockHttpServletResponse(); MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, response, chain);
        assertThat(response.getStatus()).isEqualTo(409); assertThat(response.getHeader("X-Account-Changed")).isEqualTo("true");
        assertThat(chain.getRequest()).isNull();
        req.removeHeader("X-Active-User-Id");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
        try {
            filter.doFilter(req, new MockHttpServletResponse(), (request, result) -> {
                User b = new User(); b.setId(2L); SessionUtil.login(req.getSession(), b);
                assertThat(SessionUtil.getUserId(req.getSession())).isEqualTo(1L);
            });
        } finally { RequestContextHolder.resetRequestAttributes(); }
    }

    @Test void closesOnlyConnectionsBelongingToTheChangedHttpSession() throws Exception {
        ChatWebSocketHandler sockets = new ChatWebSocketHandler();
        WebSocketSession first = connection("session-a"), otherDevice = connection("session-b");
        sockets.afterConnectionEstablished(first); sockets.afterConnectionEstablished(otherDevice);
        sockets.closeSessionConnections("session-a");
        verify(first).close(any()); verify(otherDevice, never()).close(any());
    }
    WebSocketSession connection(String id) {
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.getAttributes()).thenReturn(Map.of(ChatWebSocketHandler.USER_ID_KEY, 1L, ChatWebSocketHandler.HTTP_SESSION_ID_KEY, id));
        when(socket.isOpen()).thenReturn(true); return socket;
    }

    @Test void socketHandshakeStartedBeforeRevocationCannotRegisterAfterwards() throws Exception {
        ChatWebSocketHandler sockets = new ChatWebSocketHandler();
        sockets.closeSessionConnections("revoked");
        WebSocketSession late = connection("revoked");
        sockets.afterConnectionEstablished(late);
        verify(late).close(any());
        assertThat(sockets.isOnline(1L)).isFalse();
    }
}
