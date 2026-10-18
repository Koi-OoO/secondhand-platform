package com.secondhand.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.secondhand.entity.Category;
import com.secondhand.entity.Product;
import com.secondhand.mapper.CategoryMapper;
import com.secondhand.mapper.ProductImageMapper;
import com.secondhand.mapper.ProductMapper;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/** 只读 MySQL、幂等写入 ES。失败交给消费者重试，不向 MySQL 回写任何字段。 */
public class ProductSearchSyncWriter {
    private static final int BATCH_SIZE = 200;
    private final ElasticsearchClient client;
    private final ElasticsearchProperties properties;
    private final ProductMapper products;
    private final ProductImageMapper images;
    private final CategoryMapper categories;
    private final ProductSearchDocumentFactory factory;

    public ProductSearchSyncWriter(ElasticsearchClient client, ElasticsearchProperties properties, ProductMapper products,
            ProductImageMapper images, CategoryMapper categories, ProductSearchDocumentFactory factory) {
        this.client = client; this.properties = properties; this.products = products;
        this.images = images; this.categories = categories; this.factory = factory;
    }

    public void apply(CanalChangeDecoder.ChangePlan plan) {
        // 先处理显式 ID，确保分类事件与商品删除在同一批时也能清除已删除文档。
        List<Long> ids = new ArrayList<>(plan.productIds());
        for (int offset = 0; offset < ids.size(); offset += BATCH_SIZE) {
            refresh(ids.subList(offset, Math.min(offset + BATCH_SIZE, ids.size())));
        }
        if (!plan.refreshAll()) return;
        Long maxId = products.selectMaxId();
        if (maxId == null) return;
        long cursor = 0;
        while (cursor < maxId) {
            List<Product> batch = products.selectSearchBatch(cursor, maxId, BATCH_SIZE);
            if (batch.isEmpty()) break;
            long next = cursor;
            for (Product product : batch) {
                if (product.getId() == null || product.getId() <= next || product.getId() > maxId) {
                    throw new IllegalStateException("分类同步扫描游标无效");
                }
                next = product.getId();
            }
            refresh(batch.stream().map(Product::getId).collect(Collectors.toList()));
            cursor = next;
        }
    }

    private void refresh(List<Long> ids) {
        Map<Long, Product> current = products.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));
        Map<Integer, Category> categoryMap = categories.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, category -> category));
        var currentImages = images.selectByProductIds(ids);
        BulkRequest.Builder request = new BulkRequest.Builder().refresh(Refresh.WaitFor);
        for (Long id : ids) {
            Product product = current.get(id);
            if (product == null) {
                request.operations(op -> op.delete(del -> del.index(properties.getIndexName()).id(id.toString())));
            } else {
                var document = factory.build(product, currentImages, categoryMap);
                request.operations(op -> op.index(index -> index.index(properties.getIndexName()).id(id.toString()).document(document)));
            }
        }
        try {
            BulkRequest bulk = request.build();
            BulkResponse response = client.bulk(bulk);
            if (response.items().size() != ids.size()) throw new IllegalStateException("ES 同步响应条数不完整");
            for (int i = 0; i < ids.size(); i++) {
                var item = response.items().get(i);
                boolean missingDelete = bulk.operations().get(i).isDelete() && item.status() == 404;
                // 删除已不存在的文档是幂等成功；其他错误即使顶层 errors=false 也不能忽略。
                if (!ids.get(i).toString().equals(item.id()) || !properties.getIndexName().equals(item.index())
                        || item.error() != null || (item.status() >= 300 && !missingDelete)) {
                    throw new IllegalStateException("ES 商品同步部分失败，当前 Canal 消息不能确认");
                }
            }
        } catch (IOException ex) { throw new IllegalStateException("ES 商品同步连接失败", ex); }
    }
}
