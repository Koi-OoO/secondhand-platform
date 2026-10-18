package com.secondhand.search;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.secondhand.controller.ProductSearchController;
import com.secondhand.entity.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductSearchControllerTest {
    @Test
    void disabledSearchAndInvalidQueryHaveClearHttpStatus() throws Exception {
        ObjectProvider<ProductSearchService> provider = mock(ObjectProvider.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProductSearchController(provider)).build();
        mvc.perform(get("/product/search")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value(500));
        mvc.perform(get("/product/search").param("size", "51")).andExpect(status().isBadRequest());
        mvc.perform(get("/product/search").param("page", "abc")).andExpect(status().isBadRequest());
    }

    @Test
    void enabledSearchReturnsExistingPageEnvelopeAndUnavailableDoesNotReturnEmptySuccess() throws Exception {
        ObjectProvider<ProductSearchService> provider = mock(ObjectProvider.class);
        ProductSearchService service = mock(ProductSearchService.class);
        when(provider.getIfAvailable()).thenReturn(service);
        when(service.search(any())).thenReturn(new Page<Product>(1, 20, 0));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProductSearchController(provider)).build();
        mvc.perform(get("/product/search").param("keyword", "苹果手机"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray()).andExpect(jsonPath("$.data.size").value(20));
        when(service.search(any())).thenThrow(new ProductSearchUnavailableException("搜索服务暂时不可用", null));
        mvc.perform(get("/product/search")).andExpect(status().isServiceUnavailable());
    }
}
