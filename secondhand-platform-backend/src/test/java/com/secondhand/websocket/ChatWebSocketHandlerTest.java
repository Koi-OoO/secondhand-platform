package com.secondhand.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

class ChatWebSocketHandlerTest {

    @Test
    void isOnlineReturnsTrueWhenUserHasOpenSession() {
        ChatWebSocketHandler handler = new ChatWebSocketHandler();
        WebSocketSession session = sessionFor(7L, true);

        handler.afterConnectionEstablished(session);

        assertThat(handler.isOnline(7L)).isTrue();
    }

    @Test
    void isOnlineIgnoresClosedSessions() {
        ChatWebSocketHandler handler = new ChatWebSocketHandler();
        WebSocketSession session = sessionFor(7L, false);

        handler.afterConnectionEstablished(session);

        assertThat(handler.isOnline(7L)).isFalse();
    }

    @Test
    void recordsLastActiveTimeWhenUserConnectsAndDisconnects() {
        ChatWebSocketHandler handler = new ChatWebSocketHandler();
        WebSocketSession session = sessionFor(7L, true);

        handler.afterConnectionEstablished(session);
        Date connectedAt = handler.getLastActiveTime(7L);
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        assertThat(connectedAt).isNotNull();
        assertThat(handler.getLastActiveTime(7L)).isAfterOrEqualTo(connectedAt);
    }

    private WebSocketSession sessionFor(Long userId, boolean open) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ChatWebSocketHandler.USER_ID_KEY, userId);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(open);
        return session;
    }
}
