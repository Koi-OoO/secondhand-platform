package com.secondhand.controller;

import com.secondhand.entity.Product;
import com.secondhand.mapper.ProductMapper;
import com.secondhand.interceptor.AuthInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import javax.servlet.FilterChain;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductAvailabilityTest {
    @Test void anonymousReadReturnsOnlyPublicFieldsAndMissingProductsAreUnavailable() {
        ProductMapper mapper=mock(ProductMapper.class);
        Product p=new Product(); p.setId(40L);p.setStock(3);p.setStatus(1);p.setSellerId(99L);
        when(mapper.selectBatchIds(anyCollection())).thenReturn(List.of(p));
        var result=new ProductAvailabilityController(mapper).get(List.of(40L,41L));
        var rows=new ObjectMapper().valueToTree(result).path("data");
        assertThat(rows.get(0).path("stock").asInt()).isEqualTo(3);
        assertThat(rows.get(0).fieldNames()).toIterable().containsExactlyInAnyOrder("id","stock","status");
        assertThat(rows.get(1).path("status").asInt()).isEqualTo(4);
        assertThat(rows.get(1).path("stock").asInt()).isZero();
        verify(mapper).selectBatchIds(Set.of(40L,41L));
        verifyNoMoreInteractions(mapper);
    }
    @Test void invalidAndOversizedRequestsDoNotQueryDatabase() {
        ProductMapper mapper=mock(ProductMapper.class);var controller=new ProductAvailabilityController(mapper);
        assertThat(controller.get(List.of(-1L)).getCode()).isEqualTo(500);
        assertThat(controller.get(Collections.nCopies(51,1L)).getCode()).isEqualTo(500);
        assertThat(controller.get(List.of()).getCode()).isEqualTo(500);
        verifyNoInteractions(mapper);
    }
    @Test void onlyExactPublicGetIsWhitelisted() throws Exception {
        AuthInterceptor filter=new AuthInterceptor();FilterChain chain=mock(FilterChain.class);
        MockHttpServletRequest get=new MockHttpServletRequest("GET","/product/availability");
        MockHttpServletResponse response=new MockHttpServletResponse();
        filter.doFilter(get,response,chain);verify(chain).doFilter(get,response);
        MockHttpServletRequest post=new MockHttpServletRequest("POST","/product/availability");
        MockHttpServletResponse denied=new MockHttpServletResponse();filter.doFilter(post,denied,chain);
        assertThat(denied.getStatus()).isEqualTo(401);
    }
}
