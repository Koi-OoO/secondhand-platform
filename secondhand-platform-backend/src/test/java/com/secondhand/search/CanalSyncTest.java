package com.secondhand.search;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.protocol.CanalEntry.*;
import com.alibaba.otter.canal.protocol.Message;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证真实 protobuf 事件解析，以及确认消息的时机。 */
class CanalSyncTest {
    private final CanalChangeDecoder decoder = new CanalChangeDecoder("secondhand");

    @Test void imageMoveRefreshesBothOwnersAndProductDeleteUsesBeforeId() {
        RowData moved = RowData.newBuilder().addBeforeColumns(column("product_id", "1"))
                .addAfterColumns(column("product_id", "2")).build();
        RowData deleted = RowData.newBuilder().addBeforeColumns(column("id", "3")).build();
        var plan = decoder.decode(List.of(entry("secondhand", "product_image", EventType.UPDATE, moved),
                entry("secondhand", "product", EventType.DELETE, deleted)));
        assertThat(plan.productIds()).containsExactly(1L, 2L, 3L);
        assertThat(plan.refreshAll()).isFalse();
    }

    @Test void filtersOtherSchemasAndTablesAndDeduplicatesIds() {
        RowData row = RowData.newBuilder().addAfterColumns(column("id", "1")).build();
        var plan = decoder.decode(List.of(entry("other", "product", EventType.INSERT, row),
                entry("secondhand", "user", EventType.INSERT, row),
                entry("secondhand", "product", EventType.INSERT, row),
                entry("secondhand", "product", EventType.INSERT, row)));
        assertThat(plan.productIds()).containsExactly(1L);
    }

    @Test void categoryChangeRefreshesAllAndRelevantDdlStopsInsteadOfLosingChanges() {
        assertThat(decoder.decode(List.of(entry("secondhand", "category", EventType.UPDATE,
                RowData.getDefaultInstance()))).refreshAll()).isTrue();
        Entry ddl = Entry.newBuilder().setEntryType(EntryType.ROWDATA)
                .setHeader(Header.newBuilder().setSchemaName("secondhand").setTableName("product"))
                .setStoreValue(RowChange.newBuilder().setIsDdl(true).setEventType(EventType.ALTER).build().toByteString()).build();
        assertThatThrownBy(() -> decoder.decode(List.of(ddl))).hasMessageContaining("DDL");
        assertThatThrownBy(() -> decoder.decode(List.of(entry("secondhand", "product", EventType.INSERT,
                RowData.getDefaultInstance())))).hasMessageContaining("主键");
    }

    @Test void acknowledgementsHappenOnlyAfterSuccessfulWritesAndFailuresRollback() {
        CanalConnector connector = mock(CanalConnector.class);
        ProductSearchSyncWriter writer = mock(ProductSearchSyncWriter.class);
        CanalBatchProcessor processor = new CanalBatchProcessor(decoder, writer, 100);
        Message message = new Message(8, List.of(entry("secondhand", "product", EventType.INSERT,
                RowData.newBuilder().addAfterColumns(column("id", "1")).build())));
        when(connector.getWithoutAck(100)).thenReturn(message);
        assertThat(processor.poll(connector)).isTrue();
        var order = inOrder(writer, connector);
        order.verify(writer).apply(new CanalChangeDecoder.ChangePlan(Set.of(1L), false));
        order.verify(connector).ack(8);
        reset(connector);
        when(connector.getWithoutAck(100)).thenReturn(message);
        doThrow(new IllegalStateException("ES unavailable")).when(writer).apply(any());
        assertThatThrownBy(() -> processor.poll(connector)).hasRootCauseMessage("ES unavailable");
        verify(connector).rollback(8);
        verify(connector, never()).ack(anyLong());
    }

    @Test void emptyPollDoesNotWriteOrAcknowledgeButTransactionOnlyBatchIsAcknowledged() {
        CanalConnector connector = mock(CanalConnector.class);
        ProductSearchSyncWriter writer = mock(ProductSearchSyncWriter.class);
        CanalBatchProcessor processor = new CanalBatchProcessor(decoder, writer, 100);
        when(connector.getWithoutAck(100)).thenReturn(new Message(-1, List.of()));
        assertThat(processor.poll(connector)).isFalse();
        verifyNoInteractions(writer);
        verify(connector, never()).ack(anyLong());
        when(connector.getWithoutAck(100)).thenReturn(new Message(9, List.of(
                Entry.newBuilder().setEntryType(EntryType.TRANSACTIONEND).build())));
        assertThat(processor.poll(connector)).isTrue();
        verify(connector).ack(9);
    }

    @Test void rejectsMissingEsAndConcurrentStartupImport() {
        CanalSyncProperties p = new CanalSyncProperties();
        ElasticsearchProperties es = new ElasticsearchProperties();
        assertThatThrownBy(() -> p.validate(es)).hasMessageContaining("ES");
        es.setEnabled(true); es.setImportOnStartup(true);
        assertThatThrownBy(() -> p.validate(es)).hasMessageContaining("全量");
        es.setImportOnStartup(false);
        assertThatCode(() -> p.validate(es)).doesNotThrowAnyException();
    }

    static Column column(String name, String value) { return Column.newBuilder().setName(name).setValue(value).build(); }
    static Entry entry(String schema, String table, EventType type, RowData row) {
        return Entry.newBuilder().setEntryType(EntryType.ROWDATA)
                .setHeader(Header.newBuilder().setSchemaName(schema).setTableName(table))
                .setStoreValue(RowChange.newBuilder().setEventType(type).addRowDatas(row).build().toByteString()).build();
    }
}
