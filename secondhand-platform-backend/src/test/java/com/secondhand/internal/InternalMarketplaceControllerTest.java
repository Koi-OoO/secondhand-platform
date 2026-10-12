package com.secondhand.internal;

import com.secondhand.service.ProductService;
import com.secondhand.util.Result;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalMarketplaceControllerTest {
    @Mock
    private ProductService productService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new InternalMarketplaceController(productService)).build();
    }

    @Test
    void searchDelegatesAiQueryWithDefaults() throws Exception {
        when(productService.pageProductsForAi(1, 10, null, null, null, null, null, "latest"))
                .thenReturn(Result.success(null));

        mockMvc.perform(get("/internal/marketplace/listings/search"))
                .andExpect(status().isOk());

        verify(productService).pageProductsForAi(1, 10, null, null, null, null, null, "latest");
    }

    @Test
    void rejectsInvalidPagingWithoutCallingService() throws Exception {
        mockMvc.perform(get("/internal/marketplace/listings/search?page=0&size=21&maxPrice=-1"))
                .andExpect(status().isOk());

        verifyNoInteractions(productService);
    }

    @Test
    void detailDelegatesToReadOnlyService() throws Exception {
        when(productService.getDetailForAi(8L)).thenReturn(Result.success(null));

        mockMvc.perform(get("/internal/marketplace/listings/8"))
                .andExpect(status().isOk());

        verify(productService).getDetailForAi(8L);
    }
}
