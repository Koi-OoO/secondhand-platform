package com.secondhand.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.secondhand.entity.Category;
import com.secondhand.entity.Product;
import com.secondhand.entity.ProductImage;
import com.secondhand.mapper.CategoryMapper;
import com.secondhand.mapper.ProductImageMapper;
import com.secondhand.mapper.ProductMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 只读 MySQL、幂等写入 ES。失败交给消费者重试，不向 MySQL 回写任何字段。 */
public class ProductSearchSyncWriter {
    private static final int BATCH_SIZE = 200;
    private final ElasticsearchClient elasticsearchClient;
    private final ElasticsearchProperties properties;
    private final ProductMapper productMapper;
    private final ProductImageMapper productImageMapper;
    private final CategoryMapper categoryMapper;
    private final ProductSearchDocumentFactory documentFactory;

    public ProductSearchSyncWriter(ElasticsearchClient elasticsearchClient, ElasticsearchProperties properties,
            ProductMapper productMapper, ProductImageMapper productImageMapper, CategoryMapper categoryMapper,
            ProductSearchDocumentFactory documentFactory) {
        this.elasticsearchClient = elasticsearchClient;
        this.properties = properties;
        this.productMapper = productMapper;
        this.productImageMapper = productImageMapper;
        this.categoryMapper = categoryMapper;
        this.documentFactory = documentFactory;
    }

    public void apply(CanalChangeDecoder.ChangePlan plan) {
        // 1. 先处理消息中明确的商品ID，确保分类事件与商品删除同批时也能清除文档
        List<Long> productIds = new ArrayList<>(plan.productIds());
        for (int offset = 0; offset < productIds.size(); offset += BATCH_SIZE) {
            refreshBatch(productIds.subList(offset, Math.min(offset + BATCH_SIZE, productIds.size())));
        }

        // 2. 分类变更可能影响所有商品路径，需要按ID游标扫描全部商品
        if (!plan.refreshAll()) {
            return;
        }
        Long maxProductId = productMapper.selectMaxId();
        if (maxProductId == null) {
            return;
        }
        long cursor = 0;
        while (cursor < maxProductId) {
            List<Product> batch = productMapper.selectSearchBatch(cursor, maxProductId, BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            long nextCursor = cursor;
            for (Product product : batch) {
                if (product.getId() == null || product.getId() <= nextCursor || product.getId() > maxProductId) {
                    throw new IllegalStateException("分类同步扫描游标无效");
                }
                nextCursor = product.getId();
            }
            refreshBatch(batch.stream().map(Product::getId).collect(Collectors.toList()));
            cursor = nextCursor;
        }
    }

    private void refreshBatch(List<Long> productIds) {
        // 1. 读取当前商品、分类和图片，用最新 MySQL 数据生成文档
        Map<Long, Product> currentProducts = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));
        Map<Integer, Category> categoryMap = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, category -> category));
        List<ProductImage> currentImages = productImageMapper.selectByProductIds(productIds);

        // 2. 商品已删除则删除文档，商品存在则覆盖写入
        BulkRequest.Builder request = new BulkRequest.Builder().refresh(Refresh.WaitFor);
        for (Long productId : productIds) {
            Product product = currentProducts.get(productId);
            if (product == null) {
                request.operations(operation -> operation.delete(delete -> delete.index(properties.getIndexName())
                        .id(productId.toString())));
            } else {
                Map<String, Object> document = documentFactory.build(product, currentImages, categoryMap);
                request.operations(operation -> operation.index(index -> index.index(properties.getIndexName())
                        .id(productId.toString()).document(document)));
            }
        }

        // 3. 逐条核对批量响应；删除已不存在的文档视为幂等成功
        try {
            BulkRequest bulkRequest = request.build();
            BulkResponse response = elasticsearchClient.bulk(bulkRequest);
            if (response.items().size() != productIds.size()) {
                throw new IllegalStateException("ES 同步响应条数不完整");
            }
            for (int index = 0; index < productIds.size(); index++) {
                var item = response.items().get(index);
                boolean missingDelete = bulkRequest.operations().get(index).isDelete() && item.status() == 404;
                // 其他错误即使顶层 errors=false 也不能忽略，否则会错误确认 Canal 消息。
                if (!productIds.get(index).toString().equals(item.id())
                        || !properties.getIndexName().equals(item.index())
                        || item.error() != null
                        || (item.status() >= 300 && !missingDelete)) {
                    throw new IllegalStateException("ES 商品同步部分失败，当前 Canal 消息不能确认");
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("ES 商品同步连接失败", exception);
        }
    }
}
