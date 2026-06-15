package com.secondhand.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.secondhand.entity.Favorite;
import com.secondhand.entity.Product;
import com.secondhand.mapper.FavoriteMapper;
import com.secondhand.mapper.ProductImageMapper;
import com.secondhand.mapper.ProductMapper;
import com.secondhand.util.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FavoriteServiceImplTest {

    private FavoriteMapper favoriteMapper;
    private ProductMapper productMapper;
    private ProductImageMapper productImageMapper;
    private FavoriteServiceImpl service;

    @BeforeEach
    void setUp() {
        favoriteMapper = mock(FavoriteMapper.class);
        productMapper = mock(ProductMapper.class);
        productImageMapper = mock(ProductImageMapper.class);
        service = new FavoriteServiceImpl();
        ReflectionTestUtils.setField(service, "favoriteMapper", favoriteMapper);
        ReflectionTestUtils.setField(service, "productMapper", productMapper);
        ReflectionTestUtils.setField(service, "productImageMapper", productImageMapper);
    }

    @Test
    void addFavoriteRejectsInvalidArgumentsBeforeQueryingDatabase() {
        Result missingUser = service.addFavorite(null, 10L);
        Result missingProduct = service.addFavorite(1L, null);
        Result invalidProduct = service.addFavorite(1L, 0L);

        assertThat(missingUser.getCode()).isEqualTo(500);
        assertThat(missingUser.getMessage()).isEqualTo("参数错误");
        assertThat(missingProduct.getCode()).isEqualTo(500);
        assertThat(missingProduct.getMessage()).isEqualTo("参数错误");
        assertThat(invalidProduct.getCode()).isEqualTo(500);
        assertThat(invalidProduct.getMessage()).isEqualTo("参数错误");
        verify(productMapper, never()).selectById(any());
        verify(favoriteMapper, never()).countByUserAndProduct(any(), any());
    }

    @Test
    void addFavoriteRejectsMissingProduct() {
        when(productMapper.selectById(10L)).thenReturn(null);

        Result result = service.addFavorite(1L, 10L);

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMessage()).isEqualTo("商品不存在");
        verify(favoriteMapper, never()).insert(any(Favorite.class));
        verify(productMapper, never()).incrementLikeCount(10L, 1);
    }

    @Test
    void addFavoriteDoesNotIncrementLikeCountWhenAlreadyFavorited() {
        Product product = product(10L, 1);
        when(productMapper.selectById(10L)).thenReturn(product);
        when(favoriteMapper.countByUserAndProduct(1L, 10L)).thenReturn(1);

        Result result = service.addFavorite(1L, 10L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isEqualTo("已收藏");
        verify(favoriteMapper, never()).insert(any(Favorite.class));
        verify(productMapper, never()).incrementLikeCount(10L, 1);
    }

    @Test
    void addFavoriteCreatesFavoriteAndIncrementsLikeCount() {
        Product product = product(10L, 1);
        when(productMapper.selectById(10L)).thenReturn(product);
        when(favoriteMapper.countByUserAndProduct(1L, 10L)).thenReturn(0);

        Result result = service.addFavorite(1L, 10L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isEqualTo("收藏成功");

        ArgumentCaptor<Favorite> favoriteCaptor = ArgumentCaptor.forClass(Favorite.class);
        verify(favoriteMapper).insert(favoriteCaptor.capture());
        Favorite favorite = favoriteCaptor.getValue();
        assertThat(favorite.getUserId()).isEqualTo(1L);
        assertThat(favorite.getProductId()).isEqualTo(10L);
        assertThat(favorite.getCreateTime()).isNotNull();
        verify(productMapper).incrementLikeCount(10L, 1);
    }

    @Test
    void removeFavoriteRejectsInvalidArgumentsBeforeDeleting() {
        Result missingUser = service.removeFavorite(null, 10L);
        Result missingProduct = service.removeFavorite(1L, null);
        Result invalidProduct = service.removeFavorite(1L, 0L);

        assertThat(missingUser.getCode()).isEqualTo(500);
        assertThat(missingUser.getMessage()).isEqualTo("参数错误");
        assertThat(missingProduct.getCode()).isEqualTo(500);
        assertThat(missingProduct.getMessage()).isEqualTo("参数错误");
        assertThat(invalidProduct.getCode()).isEqualTo(500);
        assertThat(invalidProduct.getMessage()).isEqualTo("参数错误");
        verify(favoriteMapper, never()).delete(any(Wrapper.class));
        verify(productMapper, never()).incrementLikeCount(any(), any(Integer.class));
    }

    @Test
    void removeFavoriteOnlyDecrementsLikeCountWhenRecordDeleted() {
        when(favoriteMapper.delete(any(Wrapper.class))).thenReturn(1);

        Result result = service.removeFavorite(1L, 10L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isEqualTo("已取消收藏");
        verify(productMapper).incrementLikeCount(10L, -1);
    }

    @Test
    void removeFavoriteDoesNotDecrementLikeCountWhenNoRecordDeleted() {
        when(favoriteMapper.delete(any(Wrapper.class))).thenReturn(0);

        Result result = service.removeFavorite(1L, 10L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isEqualTo("已取消收藏");
        verify(productMapper, never()).incrementLikeCount(10L, -1);
    }

    private Product product(Long id, Integer status) {
        Product product = new Product();
        product.setId(id);
        product.setStatus(status);
        return product;
    }
}
