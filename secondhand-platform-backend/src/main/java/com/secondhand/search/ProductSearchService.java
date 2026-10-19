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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** ES 决定命中和顺序，MySQL 提供当前展示数据；每页批量回查，避免 N+1 查询。 */
public class ProductSearchService {
    private final ElasticsearchClient client;
    private final ElasticsearchProperties properties;
    private final ProductMapper products;
    private final ProductImageMapper images;
    private final CategoryMapper categories;
    private final ProductSearchDocumentFactory factory;

    public ProductSearchService(ElasticsearchClient client, ElasticsearchProperties properties,
            ProductMapper products, ProductImageMapper images, CategoryMapper categories, ProductSearchDocumentFactory factory) {
        this.client = client; this.properties = properties; this.products = products;
        this.images = images; this.categories = categories; this.factory = factory;
    }

    public Page<Product> search(ProductSearchCriteria criteria) {
        SearchResponse<Void> response;
        try {
            response = client.search(buildRequest(criteria), Void.class);
        } catch (IOException | ElasticsearchException ex) {
            throw new ProductSearchUnavailableException("搜索服务暂时不可用，请稍后重试", ex);
        }
        if (response.timedOut() || response.shards().failed().longValue() > 0 || response.hits().total() == null
                || response.hits().total().relation() != TotalHitsRelation.Eq) {
            throw new ProductSearchUnavailableException("搜索服务未能返回完整结果，请稍后重试", null);
        }
        Page<Product> page = new Page<>(criteria.page(), criteria.size(), response.hits().total().value());
        if (response.hits().hits().isEmpty()) return page;
        List<Long> ids = new ArrayList<>();
        for (Hit<Void> hit : response.hits().hits()) {
            try {
                long id = Long.parseLong(hit.id());
                if (id <= 0 || ids.contains(id)) throw new NumberFormatException();
                ids.add(id);
            } catch (NumberFormatException ex) {
                throw new ProductSearchUnavailableException("商品搜索索引含无效 ID，请检查索引数据", ex);
            }
        }
        Map<Long, Product> current = products.selectSearchDetailsByIds(ids).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));
        List<ProductImage> batchImages = images.selectByProductIds(ids);
        Map<Integer, Category> categoryMap = criteria.categoryId() == null ? Map.of()
                : categories.selectList(null).stream().collect(Collectors.toMap(Category::getId, category -> category));
        List<Product> records = new ArrayList<>();
        for (Long id : ids) {
            Product product = current.get(id);
            if (product == null || !stillMatches(product, criteria, categoryMap)) continue;
            product.setImages(factory.orderedImages(id, batchImages));
            product.setImageUrls(product.getImages().stream().map(ProductImage::getUrl).collect(Collectors.toList()));
            records.add(product);
        }
        // total 保留 ES 的近实时总数；过滤掉刚下架/改价的商品时，本页可能少于 size。
        page.setRecords(records);
        return page;
    }

    private SearchRequest buildRequest(ProductSearchCriteria c) {
        BoolQuery.Builder query = new BoolQuery.Builder()
                .filter(filter -> filter.term(term -> term.field("status").value(1)));
        if (!c.keyword().isEmpty()) {
            var plan = ProductKeywordQuery.parse(c.keyword());
            if (plan.isStructured()) {
                // 同组品牌别名为 OR，品牌/类型/型号各组为 AND，限制主标题以排除赠品干扰。
                int groupIndex = 0;
                for (List<String> group : plan.getRequiredGroups()) {
                    boolean primaryGroup = groupIndex++ < plan.getPrimaryGroupCount();
                    BoolQuery.Builder alternatives = new BoolQuery.Builder().minimumShouldMatch("1");
                    for (String term : group) {
                        if (primaryGroup) {
                            alternatives.should(q -> q.matchPhrase(m -> m.field("primaryTitle")
                                    .analyzer("ik_max_word").query(term)));
                        } else {
                            // 容量、健康度等剩余属性允许来自描述，不放松品牌与类型约束。
                            alternatives.should(q -> q.multiMatch(m -> m.query(term)
                                    .fields("primaryTitle^3", "description").operator(Operator.And)));
                        }
                    }
                    query.must(alternatives.build()._toQuery());
                }
                for (String term : plan.getExcludedTerms()) {
                    // 类型短语与写入分词保持一致，如“手机壳”在 IK smart/max 中切法不同。
                    query.mustNot(q -> q.matchPhrase(m -> m.field("primaryTitle").analyzer("ik_max_word").query(term)));
                }
            } else {
                query.must(must -> must.multiMatch(match -> match.query(c.keyword())
                        .fields("title^3", "description").operator(Operator.And)));
            }
        }
        if (c.categoryId() != null) {
            query.filter(filter -> filter.term(term -> term.field("categoryPathIds").value(c.categoryId())));
        }
        if (c.conditionLevel() != null) {
            query.filter(filter -> filter.term(term -> term.field("conditionLevel").value(c.conditionLevel())));
        }
        if (c.minCents() != null || c.maxCents() != null) {
            query.filter(filter -> filter.range(range -> range.longNumber(number -> number.field("priceCents")
                    .gte(c.minCents()).lte(c.maxCents()))));
        }
        SearchRequest.Builder request = new SearchRequest.Builder().index(properties.getIndexName())
                .from(c.offset()).size(c.size()).query(query.build()._toQuery()).source(source -> source.fetch(false))
                .trackTotalHits(total -> total.enabled(true)).allowPartialSearchResults(false);
        switch (c.sort()) {
            case "price_asc" -> request.sort(sort -> sort.field(field -> field.field("priceCents").order(SortOrder.Asc)));
            case "price_desc" -> request.sort(sort -> sort.field(field -> field.field("priceCents").order(SortOrder.Desc)));
            case "hottest" -> {
                request.sort(sort -> sort.field(field -> field.field("viewCount").order(SortOrder.Desc)));
                request.sort(sort -> sort.field(field -> field.field("createTime").order(SortOrder.Desc)));
            }
            default -> {
                if ("relevance".equals(c.sort()) && !c.keyword().isEmpty()) {
                    request.sort(sort -> sort.score(score -> score.order(SortOrder.Desc)));
                }
                request.sort(sort -> sort.field(field -> field.field("createTime").order(SortOrder.Desc)));
            }
        }
        request.sort(sort -> sort.field(field -> field.field("id").order(SortOrder.Desc)));
        return request.build();
    }

    private boolean stillMatches(Product p, ProductSearchCriteria c, Map<Integer, Category> categoryMap) {
        if (!Objects.equals(p.getStatus(), 1) || p.getPrice() == null
                || (c.minPrice() != null && p.getPrice().compareTo(c.minPrice()) < 0)
                || (c.maxPrice() != null && p.getPrice().compareTo(c.maxPrice()) > 0)
                || (c.conditionLevel() != null && !Objects.equals(c.conditionLevel(), p.getConditionLevel()))) return false;
        if (c.categoryId() != null) {
            try {
                return factory.categoryPath(p.getCategoryId(), categoryMap).contains(c.categoryId());
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }
        return true;
    }
}
