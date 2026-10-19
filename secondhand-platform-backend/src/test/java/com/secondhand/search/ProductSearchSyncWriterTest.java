package com.secondhand.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.OperationType;
import com.secondhand.mapper.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProductSearchSyncWriterTest {
    private final ElasticsearchClient client = mock(ElasticsearchClient.class);
    private final ProductMapper products = mock(ProductMapper.class);
    private final ProductImageMapper images = mock(ProductImageMapper.class);
    private final CategoryMapper categories = mock(CategoryMapper.class);
    private final ProductSearchSyncWriter writer = new ProductSearchSyncWriter(client, new ElasticsearchProperties(),
            products, images, categories, new ProductSearchDocumentFactory());

    @Test void rereadsCurrentDataAndDeletesMissingProductsWithStableIds() throws Exception {
        when(categories.selectList(null)).thenReturn(new ArrayList<>(ProductSearchDocumentFactoryTest.categories().values()));
        when(products.selectBatchIds(anyCollection())).thenReturn(List.of(ProductSearchDocumentFactoryTest.product()));
        when(images.selectByProductIds(anyList())).thenReturn(List.of());
        when(client.bulk(any(BulkRequest.class))).thenReturn(new BulkResponse.Builder().took(1).errors(false)
                .items(i -> i.operationType(OperationType.Index).index("campus_products").id("1").status(200))
                .items(i -> i.operationType(OperationType.Delete).index("campus_products").id("2").status(404)).build());
        writer.apply(new CanalChangeDecoder.ChangePlan(new LinkedHashSet<>(List.of(1L, 2L)), false));
        ArgumentCaptor<BulkRequest> request = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client).bulk(request.capture());
        assertThat(request.getValue().operations().get(0).index().id()).isEqualTo("1");
        assertThat(request.getValue().operations().get(1).delete().id()).isEqualTo("2");
        verify(products, never()).insert(any());
    }

    @Test void bulkItemFailureIsNotSuccessEvenWithHttp200AndFalseTopFlag() throws Exception {
        when(products.selectBatchIds(anyCollection())).thenReturn(List.of());
        when(categories.selectList(null)).thenReturn(List.of());
        when(images.selectByProductIds(anyList())).thenReturn(List.of());
        when(client.bulk(any(BulkRequest.class))).thenReturn(new BulkResponse.Builder().took(1).errors(false)
                .items(i -> i.operationType(OperationType.Delete).index("campus_products").id("1").status(500)).build());
        assertThatThrownBy(() -> writer.apply(new CanalChangeDecoder.ChangePlan(Set.of(1L), false)))
                .hasMessageContaining("同步");
    }

    @Test void categoryChangeScansBoundedBatchesAndEmptyPlanDoesNotAccessDatabase() throws Exception {
        writer.apply(new CanalChangeDecoder.ChangePlan(Set.of(), false));
        verifyNoInteractions(products, images, categories, client);
        when(products.selectMaxId()).thenReturn(2L);
        when(products.selectSearchBatch(0, 2, 200)).thenReturn(List.of(ProductSearchDocumentFactoryTest.product()));
        when(products.selectSearchBatch(1, 2, 200)).thenReturn(List.of());
        when(products.selectBatchIds(anyCollection())).thenReturn(List.of());
        when(categories.selectList(null)).thenReturn(List.of());
        when(images.selectByProductIds(anyList())).thenReturn(List.of());
        when(client.bulk(any(BulkRequest.class))).thenReturn(new BulkResponse.Builder().took(1).errors(false)
                .items(i -> i.operationType(OperationType.Delete).index("campus_products").id("1").status(200)).build());
        writer.apply(new CanalChangeDecoder.ChangePlan(Set.of(), true));
        verify(products).selectSearchBatch(1, 2, 200);
        verify(client).bulk(any(BulkRequest.class));
    }
}
