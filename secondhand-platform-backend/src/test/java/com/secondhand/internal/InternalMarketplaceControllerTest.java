package com.secondhand.internal;

import com.secondhand.service.ProductService;
import com.secondhand.util.Result;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
        mockMvc.perform(get("/internal/marketplace/listings/search?page=0&size=10&maxPrice=0"))
                .andExpect(status().isOk());

        verifyNoInteractions(productService);
    }

    @ParameterizedTest
    @CsvSource({"1,1,0", "1,20,0", "1,10,0"})
    void acceptsBoundarySearchParameters(int page, int size, String maxPrice) throws Exception {
        when(productService.pageProductsForAi(page, size, null, null, null,
                new BigDecimal(maxPrice), null, "latest")).thenReturn(Result.success(null));

        mockMvc.perform(get("/internal/marketplace/listings/search")
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size))
                        .param("maxPrice", maxPrice))
                .andExpect(status().isOk());

        verify(productService).pageProductsForAi(page, size, null, null, null,
                new BigDecimal(maxPrice), null, "latest");
    }

    @ParameterizedTest
    @CsvSource({"0,10", "1,0", "1,21"})
    void rejectsInvalidPagingValues(int page, int size) throws Exception {
        mockMvc.perform(get("/internal/marketplace/listings/search")
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size)))
                .andExpect(status().isOk());

        verifyNoInteractions(productService);
    }

    @Test
    void rejectsNegativeMaxPriceWithoutCallingService() throws Exception {
        mockMvc.perform(get("/internal/marketplace/listings/search").param("maxPrice", "-1"))
                .andExpect(status().isOk());

        verifyNoInteractions(productService);
    }

    @Test
    void mapsAllSearchFiltersToAiService() throws Exception {
        when(productService.pageProductsForAi(1, 20, 4, "phone", new BigDecimal("10.00"),
                new BigDecimal("100.00"), 2, "price_asc")).thenReturn(Result.success(null));

        mockMvc.perform(get("/internal/marketplace/listings/search")
                        .param("page", "1").param("size", "20").param("keyword", "phone")
                        .param("categoryId", "4").param("minPrice", "10.00")
                        .param("maxPrice", "100.00").param("conditionLevel", "2")
                        .param("sort", "price_asc"))
                .andExpect(status().isOk());

        verify(productService).pageProductsForAi(1, 20, 4, "phone", new BigDecimal("10.00"),
                new BigDecimal("100.00"), 2, "price_asc");
    }

    @Test
    void detailDelegatesToReadOnlyService() throws Exception {
        when(productService.getDetailForAi(8L)).thenReturn(Result.success(null));

        mockMvc.perform(get("/internal/marketplace/listings/8"))
                .andExpect(status().isOk());

        verify(productService).getDetailForAi(8L);
    }
}
