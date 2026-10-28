package com.secondhand.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.secondhand.entity.Category;
import com.secondhand.entity.Product;
import com.secondhand.entity.ProductImage;
import com.secondhand.mapper.CategoryMapper;
import com.secondhand.mapper.ProductImageMapper;
import com.secondhand.mapper.ProductMapper;
import com.secondhand.util.ProductKeywordQuery;
import com.secondhand.util.ProductKeywordQuery.Plan;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** ES 决定命中和顺序，MySQL 提供当前展示数据；每页批量回查，避免 N+1 查询。 */
public class ProductSearchService {
    private final ElasticsearchClient elasticsearchClient;
    private final ElasticsearchProperties properties;
    private final ProductMapper productMapper;
    private final ProductImageMapper productImageMapper;
    private final CategoryMapper categoryMapper;
    private final ProductSearchDocumentFactory documentFactory;

    public ProductSearchService(ElasticsearchClient elasticsearchClient, ElasticsearchProperties properties,
            ProductMapper productMapper, ProductImageMapper productImageMapper, CategoryMapper categoryMapper,
            ProductSearchDocumentFactory documentFactory) {
        this.elasticsearchClient = elasticsearchClient;
        this.properties = properties;
        this.productMapper = productMapper;
        this.productImageMapper = productImageMapper;
        this.categoryMapper = categoryMapper;
        this.documentFactory = documentFactory;
    }

    public Page<Product> search(ProductSearchCriteria criteria) {
        // 1. 执行 ES 查询，连接或查询异常统一转为搜索不可用
        SearchResponse<Void> response;
        try {
            response = elasticsearchClient.search(buildRequest(criteria), Void.class);
        } catch (IOException | ElasticsearchException exception) {
            throw new ProductSearchUnavailableException("搜索服务暂时不可用，请稍后重试", exception);
        }

        // 2. 结果不完整时明确报错，避免返回残缺分页
        if (response.timedOut()
                || response.shards().failed().longValue() > 0
                || response.hits().total() == null
                || response.hits().total().relation() != TotalHitsRelation.Eq) {
            throw new ProductSearchUnavailableException("搜索服务未能返回完整结果，请稍后重试", null);
        }
        Page<Product> page = new Page<>(criteria.page(), criteria.size(), response.hits().total().value());
        if (response.hits().hits().isEmpty()) {
            return page;
        }

        // 3. 校验 ES 返回的商品ID，拒绝无效ID和重复ID
        List<Long> productIds = new ArrayList<>();
        for (Hit<Void> hit : response.hits().hits()) {
            try {
                long productId = Long.parseLong(hit.id());
                if (productId <= 0 || productIds.contains(productId)) {
                    throw new NumberFormatException();
                }
                productIds.add(productId);
            } catch (NumberFormatException exception) {
                throw new ProductSearchUnavailableException("商品搜索索引含无效 ID，请检查索引数据", exception);
            }
        }

        // 4. 用 MySQL 当前数据覆盖 ES 中的旧快照，并批量补充图片
        Map<Long, Product> currentProducts = productMapper.selectSearchDetailsByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));
        List<ProductImage> batchImages = productImageMapper.selectByProductIds(productIds);
        Map<Integer, Category> categoryMap = criteria.categoryId() == null ? Map.of()
                : categoryMapper.selectList(null).stream().collect(Collectors.toMap(Category::getId, category -> category));

        // 5. 过滤查询期间已下架或已改价的商品，按 ES 顺序组装本页记录
        List<Product> records = new ArrayList<>();
        for (Long productId : productIds) {
            Product product = currentProducts.get(productId);
            if (product == null || !stillMatches(product, criteria, categoryMap)) {
                continue;
            }
            product.setImages(documentFactory.orderedImages(productId, batchImages));
            product.setImageUrls(product.getImages().stream().map(ProductImage::getUrl).collect(Collectors.toList()));
            records.add(product);
        }
        // total 保留 ES 的近实时总数；过滤掉刚下架/改价的商品时，本页可能少于 size。
        page.setRecords(records);
        return page;
    }

    private SearchRequest buildRequest(ProductSearchCriteria criteria) {
        // 1. 只搜索在售商品
        BoolQuery.Builder query = new BoolQuery.Builder()
                .filter(filter -> filter.term(term -> term.field("status").value(1)));

        // 2. 有结构化关键词时按品牌/类型/型号分组，否则整体匹配标题和描述
        if (!criteria.keyword().isEmpty()) {
            Plan plan = ProductKeywordQuery.parse(criteria.keyword());
            if (plan.isStructured()) {
                // 同组品牌别名为 OR，品牌/类型/型号各组为 AND，限制主标题以排除赠品干扰。
                int groupIndex = 0;
                for (List<String> group : plan.getRequiredGroups()) {
                    boolean primaryGroup = groupIndex++ < plan.getPrimaryGroupCount();
                    BoolQuery.Builder alternatives = new BoolQuery.Builder().minimumShouldMatch("1");
                    for (String term : group) {
                        if (primaryGroup) {
                            alternatives.should(should -> should.matchPhrase(match -> match.field("primaryTitle")
                                    .analyzer("ik_max_word").query(term)));
                        } else {
                            // 容量、健康度等剩余属性允许来自描述，不放松品牌与类型约束。
                            alternatives.should(should -> should.multiMatch(match -> match.query(term)
                                    .fields("primaryTitle^3", "description").operator(Operator.And)));
                        }
                    }
                    query.must(alternatives.build()._toQuery());
                }
                for (String term : plan.getExcludedTerms()) {
                    // 类型短语与写入分词保持一致，如“手机壳”在 IK smart/max 中切法不同。
                    query.mustNot(mustNot -> mustNot.matchPhrase(match -> match.field("primaryTitle")
                            .analyzer("ik_max_word").query(term)));
                }
            } else {
                query.must(must -> must.multiMatch(match -> match.query(criteria.keyword())
                        .fields("title^3", "description").operator(Operator.And)));
            }
        }

        // 3. 分类、成色和价格过滤
        if (criteria.categoryId() != null) {
            query.filter(filter -> filter.term(term -> term.field("categoryPathIds").value(criteria.categoryId())));
        }
        if (criteria.conditionLevel() != null) {
            query.filter(filter -> filter.term(term -> term.field("conditionLevel").value(criteria.conditionLevel())));
        }
        if (criteria.minCents() != null || criteria.maxCents() != null) {
            query.filter(filter -> filter.range(range -> range.longNumber(number -> number.field("priceCents")
                    .gte(criteria.minCents()).lte(criteria.maxCents()))));
        }

        // 4. 组装分页、排序和总数请求
        SearchRequest.Builder request = new SearchRequest.Builder().index(properties.getIndexName())
                .from(criteria.offset()).size(criteria.size()).query(query.build()._toQuery())
                .source(source -> source.fetch(false))
                .trackTotalHits(total -> total.enabled(true)).allowPartialSearchResults(false);
        switch (criteria.sort()) {
            case "price_asc" -> request.sort(sort -> sort.field(field -> field.field("priceCents").order(SortOrder.Asc)));
            case "price_desc" -> request.sort(sort -> sort.field(field -> field.field("priceCents").order(SortOrder.Desc)));
            case "hottest" -> {
                request.sort(sort -> sort.field(field -> field.field("viewCount").order(SortOrder.Desc)));
                request.sort(sort -> sort.field(field -> field.field("createTime").order(SortOrder.Desc)));
            }
            default -> {
                if ("relevance".equals(criteria.sort()) && !criteria.keyword().isEmpty()) {
                    request.sort(sort -> sort.score(score -> score.order(SortOrder.Desc)));
                }
                request.sort(sort -> sort.field(field -> field.field("createTime").order(SortOrder.Desc)));
            }
        }
        // 同一时间创建的商品再按ID倒序，保证分页顺序稳定。
        request.sort(sort -> sort.field(field -> field.field("id").order(SortOrder.Desc)));
        return request.build();
    }

    private boolean stillMatches(Product product, ProductSearchCriteria criteria, Map<Integer, Category> categoryMap) {
        // 1. 校验在售状态和价格
        if (!Objects.equals(product.getStatus(), 1)) {
            return false;
        }
        if (product.getPrice() == null) {
            return false;
        }
        if (criteria.minPrice() != null && product.getPrice().compareTo(criteria.minPrice()) < 0) {
            return false;
        }
        if (criteria.maxPrice() != null && product.getPrice().compareTo(criteria.maxPrice()) > 0) {
            return false;
        }

        // 2. 校验成色
        if (criteria.conditionLevel() != null
                && !Objects.equals(criteria.conditionLevel(), product.getConditionLevel())) {
            return false;
        }

        // 3. 分类按当前分类树重新计算，避免商品改分类后仍命中旧路径
        if (criteria.categoryId() != null) {
            try {
                return documentFactory.categoryPath(product.getCategoryId(), categoryMap)
                        .contains(criteria.categoryId());
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
        return true;
    }
}
