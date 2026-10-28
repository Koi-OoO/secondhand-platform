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
    private final ElasticsearchClient elasticsearchClient;
    private final ElasticsearchProperties properties;
    private final ProductMapper productMapper;
    private final ProductImageMapper productImageMapper;
    private final CategoryMapper categoryMapper;
    private final ProductSearchDocumentFactory documentFactory;
    private final ProductSearchIndexInitializer indexInitializer;

    public ProductSearchImportService(ElasticsearchClient elasticsearchClient, ElasticsearchProperties properties,
            ProductMapper productMapper, ProductImageMapper productImageMapper, CategoryMapper categoryMapper,
            ProductSearchDocumentFactory documentFactory, ProductSearchIndexInitializer indexInitializer) {
        this.elasticsearchClient = elasticsearchClient;
        this.properties = properties;
        this.productMapper = productMapper;
        this.productImageMapper = productImageMapper;
        this.categoryMapper = categoryMapper;
        this.documentFactory = documentFactory;
        this.indexInitializer = indexInitializer;
    }

    /**
     * 返回所有批次均确认成功的数量。部分失败时中断并报错，可修复原因后重复导入。
     * 这不是跨 MySQL/ES 的事务，也不负责删除历史孤立文档；增量同步另行处理。
     */
    public synchronized long importAll() {
        // 1. 先确认索引可用，再读取本次导入的商品ID上界
        indexInitializer.initialize();
        Long maxProductId = productMapper.selectMaxId();
        if (maxProductId == null) {
            return 0;
        }

        // 2. 预加载分类，供每批商品生成分类路径
        Map<Integer, Category> categoryMap = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, category -> category));

        // 3. 按商品ID游标分批导入，避免一次把全部商品读入内存
        long cursor = 0;
        long imported = 0;
        while (cursor < maxProductId) {
            List<Product> batch = productMapper.selectSearchBatch(cursor, maxProductId, BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            List<Long> productIds = batch.stream().map(Product::getId).collect(Collectors.toList());
            List<ProductImage> batchImages = productImageMapper.selectByProductIds(productIds);

            // 4. 逐商品生成文档并加入同一批 Bulk 请求
            BulkRequest.Builder request = new BulkRequest.Builder().refresh(Refresh.WaitFor);
            long nextCursor = cursor;
            for (Product product : batch) {
                if (product.getId() == null || product.getId() <= nextCursor || product.getId() > maxProductId) {
                    throw new IllegalStateException("商品导入游标读取顺序无效，已停止导入");
                }
                Map<String, Object> document;
                try {
                    document = documentFactory.build(product, batchImages, categoryMap);
                } catch (IllegalArgumentException exception) {
                    throw new IllegalStateException(
                            "商品 " + product.getId() + " 无法转换为搜索文档：" + exception.getMessage(), exception);
                }
                request.operations(operation -> operation.index(index -> index.index(properties.getIndexName())
                        .id(product.getId().toString()).document(document)));
                nextCursor = product.getId();
            }

            // 5. 执行批量写入并逐条确认，不能只看 HTTP 200
            try {
                BulkResponse response = elasticsearchClient.bulk(request.build());
                if (response.errors() || response.items().size() != batch.size()
                        || response.items().stream().anyMatch(item -> item.error() != null || item.status() >= 300)) {
                    throw new IllegalStateException("商品批量导入失败，已确认完成 " + imported
                            + " 条；当前批次可能部分成功，修复原因后可重新导入");
                }
            } catch (IOException | ElasticsearchException exception) {
                throw new IllegalStateException("商品导入中断，请检查 ES 状态；修复后可重新导入", exception);
            }

            // 6. 当前批次确认成功后，才推进游标并累计导入数量
            imported += batch.size();
            cursor = nextCursor;
            log.info("商品搜索导入进度：已确认 {} 条，当前商品 ID {}，本次上界 {}", imported, cursor, maxProductId);
        }
        return imported;
    }
}
