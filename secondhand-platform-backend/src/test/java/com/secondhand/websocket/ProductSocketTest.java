package com.secondhand.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.*;
import org.springframework.transaction.support.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class ProductSocketTest {
    WebSocketSession session(String id) {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.getId()).thenReturn(id); when(s.isOpen()).thenReturn(true);
        when(s.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 1234));
        return s;
    }

    @Test void anonymousSubscriptionGetsOnlySelectedPublicIdsAndCleansUp() throws Exception {
        ProductWebSocketHandler h = new ProductWebSocketHandler(new ObjectMapper());
        WebSocketSession a=session("a"), b=session("b");
        h.afterConnectionEstablished(a); h.afterConnectionEstablished(b);
        h.handleTextMessage(a,new TextMessage("{\"type\":\"subscribe\",\"productIds\":[40]}"));
        h.handleTextMessage(b,new TextMessage("{\"type\":\"subscribe\",\"productIds\":[41]}"));
        clearInvocations(a,b);
        h.productChanged(40L);
        ArgumentCaptor<TextMessage> sent=ArgumentCaptor.forClass(TextMessage.class);
        verify(a).sendMessage(sent.capture()); verify(b,never()).sendMessage(any());
        assertThat(new ObjectMapper().readTree(sent.getValue().getPayload()).fieldNames()).toIterable()
                .containsExactlyInAnyOrder("type","productId");
        h.afterConnectionClosed(a,CloseStatus.NORMAL); clearInvocations(a);
        h.productChanged(40L); verify(a,never()).sendMessage(any());
    }

    @Test void invalidSubscriptionsAndClientInventedChangesCannotBroadcast() throws Exception {
        ProductWebSocketHandler h=new ProductWebSocketHandler(new ObjectMapper());
        WebSocketSession a=session("a");h.afterConnectionEstablished(a);
        h.handleTextMessage(a,new TextMessage("{\"type\":\"product_changed\",\"productId\":40}"));
        verify(a).close(CloseStatus.POLICY_VIOLATION);
        WebSocketSession b=session("b");h.afterConnectionEstablished(b);
        h.handleTextMessage(b,new TextMessage("{\"type\":\"subscribe\",\"productIds\":[-1]}"));
        verify(b).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test void changeIsSentAfterCommitButNotOnRollback() {
        ProductWebSocketHandler h=mock(ProductWebSocketHandler.class);
        ProductChangePublisher publisher=new ProductChangePublisher(h);
        TransactionSynchronizationManager.initSynchronization();
        try {
            publisher.publish(40L); verifyNoInteractions(h);
            var callbacks=TransactionSynchronizationManager.getSynchronizations();
            callbacks.forEach(TransactionSynchronization::afterCommit);
            verify(h).productChanged(40L);
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
        clearInvocations(h); TransactionSynchronizationManager.initSynchronization();
        try {
            publisher.publish(41L);
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verifyNoInteractions(h);
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }

    @Test void connectionsSubscriptionsAndMessageFrequencyAreBounded() throws Exception {
        ProductWebSocketHandler h=new ProductWebSocketHandler(new ObjectMapper());
        for(int i=0;i<8;i++) h.afterConnectionEstablished(session("s"+i));
        WebSocketSession overflow=session("overflow");h.afterConnectionEstablished(overflow);
        verify(overflow).close(CloseStatus.POLICY_VIOLATION);
        h.afterConnectionClosed(session("s0"),CloseStatus.NORMAL);
        WebSocketSession next=session("next");h.afterConnectionEstablished(next);
        String ids=java.util.stream.LongStream.rangeClosed(1,51).mapToObj(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        h.handleTextMessage(next,new TextMessage("{\"type\":\"subscribe\",\"productIds\":["+ids+"]}"));
        verify(next).close(CloseStatus.POLICY_VIOLATION);
        WebSocketSession flood=session("flood");h.afterConnectionEstablished(flood);
        for(int i=0;i<11;i++) h.handleTextMessage(flood,new TextMessage("ping"));
        verify(flood).close(CloseStatus.POLICY_VIOLATION);
    }
}
