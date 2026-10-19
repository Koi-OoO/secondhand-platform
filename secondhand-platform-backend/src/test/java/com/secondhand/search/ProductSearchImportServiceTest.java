package com.secondhand.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.OperationType;
import com.secondhand.entity.Product;
import com.secondhand.mapper.CategoryMapper;
import com.secondhand.mapper.ProductImageMapper;
import com.secondhand.mapper.ProductMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductSearchImportServiceTest {
    private final ElasticsearchClient client = mock(ElasticsearchClient.class);
    private final ProductMapper products = mock(ProductMapper.class);
    private final ProductImageMapper images = mock(ProductImageMapper.class);
    private final CategoryMapper categories = mock(CategoryMapper.class);
    private final ProductSearchIndexInitializer initializer = mock(ProductSearchIndexInitializer.class);
    private ProductSearchImportService importer;

    @BeforeEach
    void setup() {
        importer = new ProductSearchImportService(client, new ElasticsearchProperties(), products, images,
                categories, new ProductSearchDocumentFactory(), initializer);
        when(categories.selectList(null)).thenReturn(new ArrayList<>(ProductSearchDocumentFactoryTest.categories().values()));
        when(images.selectByProductIds(anyList())).thenReturn(List.of());
    }

    @Test
    void cursorImportUsesStableProductIdsAndCanBeRepeated() throws Exception {
        Product p = ProductSearchDocumentFactoryTest.product();
        when(products.selectMaxId()).thenReturn(9L);
        when(products.selectSearchBatch(0L, 9L, 200)).thenReturn(List.of(p));
        when(products.selectSearchBatch(1L, 9L, 200)).thenReturn(List.of());
        when(client.bulk(any(BulkRequest.class))).thenReturn(response(false, 201, false));
        assertThat(importer.importAll()).isEqualTo(1);
        assertThat(importer.importAll()).isEqualTo(1);
        ArgumentCaptor<BulkRequest> request = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client, times(2)).bulk(request.capture());
        assertThat(request.getAllValues()).allSatisfy(bulk -> {
            assertThat(bulk.operations()).hasSize(1);
            assertThat(bulk.operations().get(0).index().id()).isEqualTo("1");
            assertThat(bulk.operations().get(0).index().index()).isEqualTo("campus_products");
        });
        verify(initializer, times(2)).initialize();
        verify(products, never()).insert(any());
    }

    @Test
    void individualBulkFailureCannotBeReportedAsSuccessEvenIfTopFlagIsFalse() throws Exception {
        when(products.selectMaxId()).thenReturn(1L);
        when(products.selectSearchBatch(0L, 1L, 200)).thenReturn(List.of(ProductSearchDocumentFactoryTest.product()));
        when(client.bulk(any(BulkRequest.class))).thenReturn(response(false, 400, true));
        assertThatThrownBy(importer::importAll).isInstanceOf(IllegalStateException.class).hasMessageContaining("批量导入失败");
        verify(products, never()).selectSearchBatch(1L, 1L, 200);
    }

    @Test
    void emptyDatabaseDoesNotSendBulkAndNetworkFailureHasActionableMessage() throws Exception {
        when(products.selectMaxId()).thenReturn(null);
        assertThat(importer.importAll()).isZero();
        verify(client, never()).bulk(any(BulkRequest.class));
        when(products.selectMaxId()).thenReturn(1L);
        when(products.selectSearchBatch(0L, 1L, 200)).thenReturn(List.of(ProductSearchDocumentFactoryTest.product()));
        when(client.bulk(any(BulkRequest.class))).thenThrow(new IOException("connection refused"));
        assertThatThrownBy(importer::importAll).isInstanceOf(IllegalStateException.class).hasMessageContaining("导入中断");
    }

    private BulkResponse response(boolean errors, int status, boolean itemError) {
        BulkResponseItem.Builder item = new BulkResponseItem.Builder().operationType(OperationType.Index)
                .index("campus_products").id("1").status(status);
        if (itemError) item.error(error -> error.type("mapper_parsing_exception").reason("invalid"));
        return new BulkResponse.Builder().took(1).errors(errors).items(item.build()).build();
    }
}
