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

    @Test
    void duplicateIdsKeepRequestOrderAndUnavailableValuesKeepTheirDefaults() {
        // 1. 准备空字段、负库存、已删除和已售出的商品
        ProductMapper mapper = mock(ProductMapper.class);
        Product nullFields = new Product();
        nullFields.setId(40L);
        Product negativeStock = new Product();
        negativeStock.setId(41L);
        negativeStock.setStock(-2);
        negativeStock.setStatus(1);
        Product deleted = new Product();
        deleted.setId(42L);
        deleted.setStock(8);
        deleted.setStatus(4);
        Product sold = new Product();
        sold.setId(43L);
        sold.setStock(0);
        sold.setStatus(3);
        when(mapper.selectBatchIds(anyCollection())).thenReturn(List.of(deleted, nullFields, sold, negativeStock));

        // 2. 请求包含重复ID，返回结果应按首次出现的顺序排列
        var result = new ProductAvailabilityController(mapper).get(List.of(43L, 40L, 41L, 42L, 43L));
        var rows = new ObjectMapper().valueToTree(result).path("data");

        // 3. 核对库存和状态的原有默认值，不受数据库返回顺序影响
        assertThat(rows.size()).isEqualTo(4);
        assertThat(rows.get(0).path("id").asLong()).isEqualTo(43L);
        assertThat(rows.get(0).path("status").asInt()).isEqualTo(3);
        assertThat(rows.get(1).path("id").asLong()).isEqualTo(40L);
        assertThat(rows.get(1).path("status").asInt()).isEqualTo(2);
        assertThat(rows.get(2).path("id").asLong()).isEqualTo(41L);
        assertThat(rows.get(2).path("status").asInt()).isEqualTo(1);
        assertThat(rows.get(3).path("id").asLong()).isEqualTo(42L);
        assertThat(rows.get(3).path("status").asInt()).isEqualTo(4);
        for (var row : rows) {
            assertThat(row.path("stock").asInt()).isZero();
        }
        verify(mapper).selectBatchIds(Set.of(40L, 41L, 42L, 43L));
    }
}
