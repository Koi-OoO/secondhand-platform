package com.secondhand.search;

import com.alibaba.otter.canal.client.impl.SimpleCanalConnector;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

/** 单消费者顺序处理；连接或 ES 故障后退避重连，不跳过失败消息。 */
public class CanalSyncWorker implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(CanalSyncWorker.class);
    private final CanalSyncProperties properties;
    private final CanalBatchProcessor processor;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "product-search-canal"); thread.setDaemon(true); return thread;
    });
    private volatile boolean running;
    private volatile SimpleCanalConnector current;
    private volatile Instant lastSuccess;
    private volatile long failures;

    public CanalSyncWorker(CanalSyncProperties properties, CanalBatchProcessor processor) {
        this.properties = properties; this.processor = processor;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        executor.submit(this::consume);
    }

    private void consume() {
        while (running && !Thread.currentThread().isInterrupted()) {
            SimpleCanalConnector connector = new SimpleCanalConnector(
                    new InetSocketAddress(properties.getHost(), properties.getPort()), properties.getUsername(),
                    properties.getPassword(), properties.getDestination());
            connector.setSoTimeout(properties.getSocketTimeoutMillis());
            current = connector;
            try {
                connector.connect();
                connector.subscribe(properties.filter());
                connector.rollback(); // 重连后从最近确认的消费位点继续。
                log.info("Canal 商品同步已连接，destination={}，等待变化；连接成功不代表已完成追赶", properties.getDestination());
                while (running && !Thread.currentThread().isInterrupted()) {
                    if (processor.poll(connector)) lastSuccess = Instant.now();
                    else if (!pause(properties.getIdleDelayMillis())) return;
                }
            } catch (Exception ex) {
                if (running) {
                    failures++;
                    // 不记录异常原文或完整事件，避免客户端错误包含密码、SQL 或商品行数据。
                    log.warn("Canal 同步失败，将保留消息并重试；异常类型={}，累计失败={}，最后成功时间={}",
                            rootType(ex), failures, lastSuccess);
                }
            } finally {
                try { connector.disconnect(); } catch (Exception ignored) { }
                current = null;
            }
            if (running && !pause(properties.getRetryDelayMillis())) return;
        }
    }

    private String rootType(Throwable ex) {
        while (ex.getCause() != null && ex.getCause() != ex) ex = ex.getCause();
        return ex.getClass().getSimpleName();
    }

    private boolean pause(long millis) {
        try { Thread.sleep(millis); return true; }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); return false; }
    }

    public Instant getLastSuccess() { return lastSuccess; }
    public long getFailures() { return failures; }

    /** 应用停止时先中断消费者，再关闭连接，不自动确认尚未完成的消息。 */
    @Override public synchronized void destroy() {
        running = false;
        executor.shutdownNow();
        SimpleCanalConnector connector = current;
        if (connector != null) try { connector.disconnect(); } catch (Exception ignored) { }
        try { executor.awaitTermination(3, TimeUnit.SECONDS); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
    }
}
