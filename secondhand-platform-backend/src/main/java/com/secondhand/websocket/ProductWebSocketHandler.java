package com.secondhand.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** 公开商品事件连接：游客仅能订阅商品 ID，不接收订单、会话或用户数据。单实例实现。 */
@Component
public class ProductWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper json;
    private final Map<String, Client> clients = new ConcurrentHashMap<>();
    private static class Client {
        final WebSocketSession socket;
        volatile Set<Long> ids = Collections.emptySet();
        final String address;
        long window = System.currentTimeMillis();
        int received;
        Client(WebSocketSession s) { socket=s; address=String.valueOf(s.getRemoteAddress() == null ? null : s.getRemoteAddress().getAddress()); }
    }
    public ProductWebSocketHandler(ObjectMapper json) { this.json=json; }

    @Override public synchronized void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Client client=new Client(session);
        if (clients.size() >= 256 || clients.values().stream().filter(c -> c.address.equals(client.address)).count() >= 8) {
            session.close(CloseStatus.POLICY_VIOLATION); return;
        }
        session.setTextMessageSizeLimit(4096);
        clients.put(session.getId(),client);
    }
    @Override public void afterConnectionClosed(WebSocketSession s, CloseStatus status) { clients.remove(s.getId()); }
    @Override public void handleTransportError(WebSocketSession s, Throwable error) throws Exception { reject(s); }

    @Override public void handleTextMessage(WebSocketSession s, TextMessage message) throws IOException {
        Client c=clients.get(s.getId());
        if(c==null) return;
        synchronized(c) {
            long now=System.currentTimeMillis();
            if(now-c.window>=1000) { c.window=now; c.received=0; }
            if(++c.received>10 || message.getPayloadLength()>4096) { reject(s); return; }
        }
        if("ping".equals(message.getPayload())) { send(c,"pong"); return; }
        try {
            JsonNode request=json.readTree(message.getPayload());
            if(!"subscribe".equals(request.path("type").asText()) || !request.path("productIds").isArray() || request.path("productIds").size()>50) { reject(s); return; }
            Set<Long> ids=new HashSet<>();
            for(JsonNode id:request.path("productIds")) {
                if(!id.isIntegralNumber() || !id.canConvertToLong() || id.asLong()<=0) { reject(s); return; }
                ids.add(id.asLong());
            }
            c.ids=Collections.unmodifiableSet(ids);
            send(c,"{\"type\":\"subscribed\"}");
        } catch(Exception ex) { reject(s); }
    }
    public void productChanged(Long id) {
        if(id==null || id<=0) return;
        String event="{\"type\":\"product_changed\",\"productId\":"+id+"}";
        clients.values().stream().filter(c -> c.ids.contains(id)).forEach(c -> send(c,event));
    }
    private void send(Client c,String text) {
        try {
            synchronized(c.socket) {
                if(c.socket.isOpen()) c.socket.sendMessage(new TextMessage(text));
                else clients.remove(c.socket.getId());
            }
        } catch(Exception ex) {
            clients.remove(c.socket.getId());
            try { c.socket.close(CloseStatus.SERVER_ERROR); } catch(Exception ignored) { }
        }
    }
    private void reject(WebSocketSession s) throws IOException { clients.remove(s.getId()); s.close(CloseStatus.POLICY_VIOLATION); }
}
