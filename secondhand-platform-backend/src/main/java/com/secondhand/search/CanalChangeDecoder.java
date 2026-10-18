package com.secondhand.search;

import com.alibaba.otter.canal.protocol.CanalEntry.*;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 将 binlog 事件转换为需要刷新的商品 ID，不信任事件中的旧价格和旧状态。 */
public class CanalChangeDecoder {
    private final String schema;

    public CanalChangeDecoder(String schema) { this.schema = schema; }

    /** 分类可能影响后代路径，因此分类变更要求分批刷新全部现存商品。 */
    public record ChangePlan(Set<Long> productIds, boolean refreshAll) {
        public ChangePlan { productIds = Collections.unmodifiableSet(new LinkedHashSet<>(productIds)); }
    }

    public ChangePlan decode(List<Entry> entries) {
        Set<Long> ids = new LinkedHashSet<>();
        boolean refreshAll = false;
        for (Entry entry : entries) {
            if (entry.getEntryType() != EntryType.ROWDATA || !schema.equals(entry.getHeader().getSchemaName())) continue;
            String table = entry.getHeader().getTableName();
            // 空表名的库级 DDL 也必须停下，不能确认后悄悄跳过结构变化。
            if (!table.isEmpty() && !Set.of("product", "product_image", "category").contains(table)) continue;
            RowChange change;
            try { change = RowChange.parseFrom(entry.getStoreValue()); }
            catch (InvalidProtocolBufferException ex) { throw new IllegalStateException("Canal 事件无法解析，保留消息等待处理", ex); }
            if (change.getIsDdl() || !Set.of(EventType.INSERT, EventType.UPDATE, EventType.DELETE).contains(change.getEventType())) {
                throw new IllegalStateException("检测到 DDL 或不支持的事件，请先验证搜索映射并重建索引；消息未确认");
            }
            if (table.isEmpty()) throw new IllegalStateException("Canal 事件缺少表名");
            if ("category".equals(table)) { refreshAll = true; continue; }
            if (change.getRowDatasCount() == 0) throw new IllegalStateException("Canal 事件缺少主键行数据");
            for (RowData row : change.getRowDatasList()) {
                String key = "product_image".equals(table) ? "product_id" : "id";
                // 图片可能由 A 商品迁到 B 商品，必须同时更新两个商品的封面。
                if (change.getEventType() != EventType.INSERT) ids.add(readId(row.getBeforeColumnsList(), key));
                if (change.getEventType() != EventType.DELETE) ids.add(readId(row.getAfterColumnsList(), key));
            }
        }
        return new ChangePlan(ids, refreshAll);
    }

    private long readId(List<Column> columns, String name) {
        for (Column column : columns) {
            if (!name.equals(column.getName()) || column.getIsNull()) continue;
            try {
                long id = Long.parseLong(column.getValue());
                if (id > 0) return id;
            } catch (NumberFormatException ignored) { /* 下方统一报错，不输出原始行数据。 */ }
        }
        throw new IllegalStateException("Canal 事件缺少有效主键，请检查 FULL 行日志配置");
    }
}
