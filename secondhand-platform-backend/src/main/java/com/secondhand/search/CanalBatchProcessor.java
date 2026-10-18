package com.secondhand.search;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.protocol.CanalEntry;
import java.util.ArrayList;

/** 单批处理边界：所有 ES 操作成功才 ack，否则 rollback，允许整批重复消费。 */
public class CanalBatchProcessor {
    private final CanalChangeDecoder decoder;
    private final ProductSearchSyncWriter writer;
    private final int batchSize;

    public CanalBatchProcessor(CanalChangeDecoder decoder, ProductSearchSyncWriter writer, int batchSize) {
        this.decoder = decoder; this.writer = writer; this.batchSize = batchSize;
    }

    public boolean poll(CanalConnector connector) {
        var message = connector.getWithoutAck(batchSize);
        if (message.getId() == -1) return false;
        try {
            var entries = message.getEntries();
            if (message.isRaw()) {
                entries = new ArrayList<>();
                for (var raw : message.getRawEntries()) entries.add(CanalEntry.Entry.parseFrom(raw));
            }
            writer.apply(decoder.decode(entries));
            connector.ack(message.getId());
            return true;
        } catch (Exception ex) {
            try { connector.rollback(message.getId()); }
            catch (Exception rollbackFailure) { ex.addSuppressed(rollbackFailure); }
            throw new IllegalStateException("Canal 批次处理失败，消息将重试", ex);
        }
    }
}
