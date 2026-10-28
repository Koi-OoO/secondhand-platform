package com.secondhand.search;

import com.alibaba.otter.canal.protocol.CanalEntry.Column;
import com.alibaba.otter.canal.protocol.CanalEntry.Entry;
import com.alibaba.otter.canal.protocol.CanalEntry.EntryType;
import com.alibaba.otter.canal.protocol.CanalEntry.EventType;
import com.alibaba.otter.canal.protocol.CanalEntry.RowChange;
import com.alibaba.otter.canal.protocol.CanalEntry.RowData;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 将 binlog 事件转换为需要刷新的商品 ID，不信任事件中的旧价格和旧状态。 */
public class CanalChangeDecoder {
    private final String schema;

    public CanalChangeDecoder(String schema) {
        this.schema = schema;
    }

    /** 分类可能影响后代路径，因此分类变更要求分批刷新全部现存商品。 */
    public record ChangePlan(Set<Long> productIds, boolean refreshAll) {
        public ChangePlan {
            productIds = Collections.unmodifiableSet(new LinkedHashSet<>(productIds));
        }
    }

    public ChangePlan decode(List<Entry> entries) {
        // 1. 收集需要刷新的商品ID；分类变更单独标记为全量刷新
        Set<Long> productIds = new LinkedHashSet<>();
        boolean refreshAll = false;
        for (Entry entry : entries) {
            if (entry.getEntryType() != EntryType.ROWDATA
                    || !schema.equals(entry.getHeader().getSchemaName())) {
                continue;
            }

            // 2. 只处理商品、图片和分类表；空表名的库级DDL必须停下来
            String table = entry.getHeader().getTableName();
            if (!table.isEmpty() && !Set.of("product", "product_image", "category").contains(table)) {
                continue;
            }

            // 3. 解析行变更，拒绝DDL和不支持的事件类型，避免跳过结构变化
            RowChange change;
            try {
                change = RowChange.parseFrom(entry.getStoreValue());
            } catch (InvalidProtocolBufferException exception) {
                throw new IllegalStateException("Canal 事件无法解析，保留消息等待处理", exception);
            }
            if (change.getIsDdl()
                    || !Set.of(EventType.INSERT, EventType.UPDATE, EventType.DELETE)
                            .contains(change.getEventType())) {
                throw new IllegalStateException("检测到 DDL 或不支持的事件，请先验证搜索映射并重建索引；消息未确认");
            }
            if (table.isEmpty()) {
                throw new IllegalStateException("Canal 事件缺少表名");
            }
            if ("category".equals(table)) {
                refreshAll = true;
                continue;
            }
            if (change.getRowDatasCount() == 0) {
                throw new IllegalStateException("Canal 事件缺少主键行数据");
            }

            // 4. 更新和删除要带旧值，插入和更新要带新值；图片迁移需同时刷新两个商品
            for (RowData row : change.getRowDatasList()) {
                String idColumn = "product_image".equals(table) ? "product_id" : "id";
                if (change.getEventType() != EventType.INSERT) {
                    productIds.add(readId(row.getBeforeColumnsList(), idColumn));
                }
                if (change.getEventType() != EventType.DELETE) {
                    productIds.add(readId(row.getAfterColumnsList(), idColumn));
                }
            }
        }
        return new ChangePlan(productIds, refreshAll);
    }

    private long readId(List<Column> columns, String name) {
        for (Column column : columns) {
            if (!name.equals(column.getName()) || column.getIsNull()) {
                continue;
            }
            try {
                long id = Long.parseLong(column.getValue());
                if (id > 0) {
                    return id;
                }
            } catch (NumberFormatException ignored) {
                // 下方统一报错，不输出原始行数据
            }
        }
        throw new IllegalStateException("Canal 事件缺少有效主键，请检查 FULL 行日志配置");
    }
}
