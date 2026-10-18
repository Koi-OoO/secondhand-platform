package com.secondhand.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.secondhand.entity.Product;
import com.secondhand.entity.ProductImage;
import com.secondhand.mapper.CategoryMapper;
import com.secondhand.mapper.ProductImageMapper;
import com.secondhand.mapper.ProductMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductSearchServiceTest {
    private final ElasticsearchClient client = mock(ElasticsearchClient.class);
    private final ProductMapper products = mock(ProductMapper.class);
    private final ProductImageMapper images = mock(ProductImageMapper.class);
    private final CategoryMapper categories = mock(CategoryMapper.class);
    private ProductSearchService service;

    @BeforeEach
    void setup() {
        service = new ProductSearchService(client, new ElasticsearchProperties(), products, images,
                categories, new ProductSearchDocumentFactory());
        when(products.selectSearchDetailsByIds(anyList())).thenReturn(List.of());
        when(images.selectByProductIds(anyList())).thenReturn(List.of());
        when(categories.selectList(null)).thenReturn(new ArrayList<>(ProductSearchDocumentFactoryTest.categories().values()));
    }

    @Test
    void queryRequiresAllWordsFiltersPublicProductsAndUsesStablePriceOrder() throws Exception {
        when(client.search(any(SearchRequest.class), eq(Void.class))).thenReturn(response(false, 0));
        service.search(new ProductSearchCriteria(2, 20, "苹果 手机", 11,
                new BigDecimal("10.001"), new BigDecimal("2000.999"), 2, "price_asc"));
        ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client).search(request.capture(), eq(Void.class));
        SearchRequest r = request.getValue();
        assertThat(r.from()).isEqualTo(20); assertThat(r.size()).isEqualTo(20);
        assertThat(r.trackTotalHits().enabled()).isTrue();
        assertThat(r.allowPartialSearchResults()).isFalse();
        assertThat(r.query().bool().must().get(0).multiMatch().operator()).isEqualTo(Operator.And);
        assertThat(r.query().bool().must().get(0).multiMatch().fields()).containsExactly("title^3", "description");
        assertThat(r.query().bool().filter()).hasSize(4);
        assertThat(r.query().toString()).contains("categoryPathIds", "priceCents", "1001", "200099", "status", "conditionLevel");
        assertThat(r.sort().get(0).field().field()).isEqualTo("priceCents");
        assertThat(r.sort().get(0).field().order()).isEqualTo(SortOrder.Asc);
        assertThat(r.sort().get(1).field().field()).isEqualTo("id");
    }

    @Test
    void responseRestoresEsOrderAndCurrentMySqlImagesAndHidesChangedProducts() throws Exception {
        Product p1 = ProductSearchDocumentFactoryTest.product();
        Product p2 = ProductSearchDocumentFactoryTest.product(); p2.setId(2L);
        Product withdrawn = ProductSearchDocumentFactoryTest.product(); withdrawn.setId(3L); withdrawn.setStatus(2);
        Product expensive = ProductSearchDocumentFactoryTest.product(); expensive.setId(4L); expensive.setPrice(new BigDecimal("5000"));
        Product wrongCategory = ProductSearchDocumentFactoryTest.product(); wrongCategory.setId(5L); wrongCategory.setCategoryId(10);
        when(client.search(any(SearchRequest.class), eq(Void.class))).thenReturn(response(false, 5, 2, 1, 3, 4, 5));
        when(products.selectSearchDetailsByIds(List.of(2L, 1L, 3L, 4L, 5L)))
                .thenReturn(List.of(p1, p2, withdrawn, expensive, wrongCategory));
        ProductImage cover = ProductSearchDocumentFactoryTest.image(2, 2, 0, "/current.jpg");
        ProductImage second = ProductSearchDocumentFactoryTest.image(3, 2, 1, "/second.jpg");
        when(images.selectByProductIds(anyList())).thenReturn(List.of(second, cover));
        Page<Product> result = service.search(new ProductSearchCriteria(1, 20, "苹果手机", 11,
                null, new BigDecimal("2000"), null, "relevance"));
        assertThat(result.getRecords()).extracting(Product::getId).containsExactly(2L, 1L);
        assertThat(result.getRecords().get(0).getImages()).extracting(ProductImage::getUrl)
                .containsExactly("/current.jpg", "/second.jpg");
        assertThat(result.getTotal()).isEqualTo(5); // 近实时索引总数，不能伪装成 MySQL 强一致计数。
        verify(products, times(1)).selectSearchDetailsByIds(anyList());
        verify(images, times(1)).selectByProductIds(anyList());
    }

    @Test
    void emptySearchDoesNotSendEmptyInClause() throws Exception {
        when(client.search(any(SearchRequest.class), eq(Void.class))).thenReturn(response(false, 0));
        assertThat(service.search(criteria()).getRecords()).isEmpty();
        verifyNoInteractions(products, images, categories);
    }

    @Test
    void timeoutShardFailureAndConnectionFailureCannotPretendToBeNoMatches() throws Exception {
        when(client.search(any(SearchRequest.class), eq(Void.class))).thenReturn(response(true, 0));
        assertThatThrownBy(() -> service.search(criteria())).isInstanceOf(ProductSearchUnavailableException.class);
        when(client.search(any(SearchRequest.class), eq(Void.class))).thenThrow(new IOException("offline"));
        assertThatThrownBy(() -> service.search(criteria())).isInstanceOf(ProductSearchUnavailableException.class)
                .hasMessageContaining("搜索服务");
        verifyNoInteractions(products);
    }

    private ProductSearchCriteria criteria() {
        return new ProductSearchCriteria(1, 20, null, null, null, null, null, "latest");
    }

    private SearchResponse<Void> response(boolean timeout, long total, long... ids) {
        List<Hit<Void>> hits = new ArrayList<>();
        for (long id : ids) hits.add(new Hit.Builder<Void>().index("campus_products_v2").id(Long.toString(id)).build());
        return new SearchResponse.Builder<Void>().took(1).timedOut(timeout)
                .shards(shards -> shards.total(1).successful(1).failed(0))
                .hits(h -> h.total(t -> t.value(total).relation(TotalHitsRelation.Eq)).hits(hits)).build();
    }
}
