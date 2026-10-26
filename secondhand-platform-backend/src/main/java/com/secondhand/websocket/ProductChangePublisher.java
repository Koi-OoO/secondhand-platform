package com.secondhand.websocket;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 数据提交后通知浏览器补查；推送失败不能回滚已完成的商品/订单操作。 */
@Component
public class ProductChangePublisher {
    private final ProductWebSocketHandler sockets;
    public ProductChangePublisher(ProductWebSocketHandler sockets) { this.sockets=sockets; }
    public void publish(Long id) {
        Runnable send=() -> { try { sockets.productChanged(id); } catch(Exception ignored) { } };
        if(TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { send.run(); }
            });
        } else send.run();
    }
}
