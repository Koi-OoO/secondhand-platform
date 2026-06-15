package com.secondhand.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.secondhand.entity.Category;
import com.secondhand.entity.Product;
import com.secondhand.mapper.CategoryMapper;
import com.secondhand.mapper.ProductImageMapper;
import com.secondhand.mapper.ProductMapper;
import com.secondhand.util.CacheUtil;
import com.secondhand.util.Result;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceImplTest {

    private ProductMapper productMapper;
    private ProductImageMapper productImageMapper;
    private CategoryMapper categoryMapper;
    private RedisTemplate<String, Object> redisTemplate;
    private RedissonClient redissonClient;
    private CacheUtil cacheUtil;
    private ProductServiceImpl service;

    @BeforeEach
    void setUp() {
        productMapper = mock(ProductMapper.class);
        productImageMapper = mock(ProductImageMapper.class);
        categoryMapper = mock(CategoryMapper.class);
        redisTemplate = mock(RedisTemplate.class);
        redissonClient = mock(RedissonClient.class);
        cacheUtil = mock(CacheUtil.class);
        service = new ProductServiceImpl();
        ReflectionTestUtils.setField(service, "productMapper", productMapper);
        ReflectionTestUtils.setField(service, "productImageMapper", productImageMapper);
        ReflectionTestUtils.setField(service, "categoryMapper", categoryMapper);
        ReflectionTestUtils.setField(service, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(service, "redissonClient", redissonClient);
        ReflectionTestUtils.setField(service, "cacheUtil", cacheUtil);
    }

    @Test
    void publishRequiresStockFieldForInventoryContract() {
        Field stockField = requireField(Product.class, "stock");

        assertThat(stockField.getType()).isEqualTo(Integer.class);
    }

    @Test
    void publishRejectsNonPositiveStockBeforeWriting() {
        Product product = validProduct();
        setRequiredField(product, "stock", 0);

        Result result = service.publish(7L, product, List.of("https://img.example.com/1.png"));

        assertThat(result.getCode()).isEqualTo(500);
        verify(productMapper, never()).insert(any(Product.class));
    }

    @Test
    void publishPersistsStockWhenValid() {
        Product product = validProduct();
        setRequiredField(product, "stock", 6);
        when(categoryMapper.selectById(3)).thenReturn(validCategory());

        Result result = service.publish(7L, product, List.of("https://img.example.com/1.png"));

        assertThat(result.getCode()).isEqualTo(200);
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productMapper).insert(productCaptor.capture());
        assertThat(readRequiredField(productCaptor.getValue(), "stock")).isEqualTo(6);
        assertThat(productCaptor.getValue().getStatus()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getDetailReturnsProductWhenCacheUtilResolvesIt() {
        Product product = new Product();
        product.setId(40L);
        when(cacheUtil.<Long, Product>queryWithLogicalExpire(
                any(), eq(40L), eq(Product.class), any(), anyLong(), anyLong()))
                .thenReturn(product);
        ValueOperations<String, Object> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);

        Result result = service.getDetail(40L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isSameAs(product);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getDetailReturnsNotFoundWhenCacheUtilReturnsNull() {
        when(cacheUtil.<Long, Product>queryWithLogicalExpire(
                any(), eq(40L), eq(Product.class), any(), anyLong(), anyLong()))
                .thenReturn(null);

        Result result = service.getDetail(40L);

        assertThat(result.getCode()).isEqualTo(500);
    }

    @Test
    void getSellerProductsShowsOnlyOnSaleToVisitors() {
        Page<Product> emptyPage = new Page<>(1, 12);
        emptyPage.setRecords(Collections.emptyList());
        when(productMapper.selectPageWithDetails(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(emptyPage);

        service.getSellerProducts(6L, 1, 12, 99L);

        verify(productMapper).selectPageWithDetails(
                any(), eq(6L), any(), any(), any(), any(), any(), eq(1), any());
    }

    @Test
    void getSellerProductsShowsAllStatusesToOwner() {
        Page<Product> emptyPage = new Page<>(1, 12);
        emptyPage.setRecords(Collections.emptyList());
        when(productMapper.selectPageWithDetails(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(emptyPage);

        service.getSellerProducts(6L, 1, 12, 6L);

        verify(productMapper).selectPageWithDetails(
                any(), eq(6L), any(), any(), any(), any(), any(), isNull(), any());
    }

    private Product validProduct() {
        Product product = new Product();
        product.setTitle("Nintendo Switch");
        product.setDescription("Lightly used console");
        product.setPrice(new BigDecimal("1499.00"));
        product.setOriginalPrice(new BigDecimal("1999.00"));
        product.setCategoryId(3);
        product.setConditionLevel(2);
        return product;
    }

    private Category validCategory() {
        Category category = new Category();
        category.setId(3);
        category.setName("Game");
        return category;
    }

    private Field requireField(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException ex) {
            throw new AssertionError("Expected field " + type.getSimpleName() + "." + name + " to exist", ex);
        }
    }

    private void setRequiredField(Object target, String name, Object value) {
        requireField(target.getClass(), name);
        ReflectionTestUtils.setField(target, name, value);
    }

    private Object readRequiredField(Object target, String name) {
        requireField(target.getClass(), name);
        return ReflectionTestUtils.getField(target, name);
    }
}
