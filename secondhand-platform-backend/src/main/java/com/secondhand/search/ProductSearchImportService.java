package com.secondhand.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 显式触发的初始化导入：只读取 MySQL，以商品 ID 覆盖同名 ES 文档，不清空索引。 */
public class ProductSearchImportService {
    private static final Logger log = LoggerFactory.getLogger(ProductSearchImportService.class);
    private static final int BATCH_SIZE = 200;
    private final ElasticsearchClient client;
    private final ElasticsearchProperties properties;
    private final ProductMapper products;
    private final ProductImageMapper images;
    private final CategoryMapper categories;
    private final ProductSearchDocumentFactory factory;
    private final ProductSearchIndexInitializer initializer;

    public ProductSearchImportService(ElasticsearchClient client, ElasticsearchProperties properties,
            ProductMapper products, ProductImageMapper images, CategoryMapper categories,
            ProductSearchDocumentFactory factory, ProductSearchIndexInitializer initializer) {
        this.client = client; this.properties = properties; this.products = products;
        this.images = images; this.categories = categories; this.factory = factory; this.initializer = initializer;
    }

    /**
     * 返回所有批次均确认成功的数量。部分失败时中断并报错，可修复原因后重复导入。
     * 这不是跨 MySQL/ES 的事务，也不负责删除历史孤立文档；增量同步另行处理。
     */
    public synchronized long importAll() {
        initializer.initialize();
        Long upperBound = products.selectMaxId();
        if (upperBound == null) return 0;
        Map<Integer, Category> categoryMap = categories.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, category -> category));
        long cursor = 0;
        long imported = 0;
        while (cursor < upperBound) {
            List<Product> batch = products.selectSearchBatch(cursor, upperBound, BATCH_SIZE);
            if (batch.isEmpty()) break;
            List<Long> ids = batch.stream().map(Product::getId).collect(Collectors.toList());
            List<ProductImage> batchImages = images.selectByProductIds(ids);
            BulkRequest.Builder request = new BulkRequest.Builder().refresh(Refresh.WaitFor);
            long nextCursor = cursor;
            for (Product product : batch) {
                if (product.getId() == null || product.getId() <= nextCursor || product.getId() > upperBound) {
                    throw new IllegalStateException("商品导入游标读取顺序无效，已停止导入");
                }
                Map<String, Object> document;
                try {
                    document = factory.build(product, batchImages, categoryMap);
                } catch (IllegalArgumentException ex) {
                    throw new IllegalStateException("商品 " + product.getId() + " 无法转换为搜索文档：" + ex.getMessage(), ex);
                }
                request.operations(op -> op.index(index -> index.index(properties.getIndexName())
                        .id(product.getId().toString()).document(document)));
                nextCursor = product.getId();
            }
            try {
                BulkResponse response = client.bulk(request.build());
                // Bulk 的 HTTP 200 只代表请求已处理，必须逐条检查错误。
                if (response.errors() || response.items().size() != batch.size()
                        || response.items().stream().anyMatch(item -> item.error() != null || item.status() >= 300)) {
                    throw new IllegalStateException("商品批量导入失败，已确认完成 " + imported
                            + " 条；当前批次可能部分成功，修复原因后可重新导入");
                }
            } catch (IOException | ElasticsearchException ex) {
                throw new IllegalStateException("商品导入中断，请检查 ES 状态；修复后可重新导入", ex);
            }
            imported += batch.size();
            cursor = nextCursor;
            log.info("商品搜索导入进度：已确认 {} 条，当前商品 ID {}，本次上界 {}", imported, cursor, upperBound);
        }
        return imported;
    }
}
