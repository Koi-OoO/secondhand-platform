package com.secondhand.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.secondhand.entity.Favorite;
import com.secondhand.entity.Product;
import com.secondhand.entity.ProductImage;
import com.secondhand.mapper.FavoriteMapper;
import com.secondhand.mapper.ProductImageMapper;
import com.secondhand.mapper.ProductMapper;
import com.secondhand.service.NotificationService;
import com.secondhand.util.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
    private NotificationService notificationService;
    private FavoriteServiceImpl service;

    @BeforeEach
    void setUp() {
        favoriteMapper = mock(FavoriteMapper.class);
        productMapper = mock(ProductMapper.class);
        productImageMapper = mock(ProductImageMapper.class);
        notificationService = mock(NotificationService.class);
        service = new FavoriteServiceImpl();
        ReflectionTestUtils.setField(service, "favoriteMapper", favoriteMapper);
        ReflectionTestUtils.setField(service, "productMapper", productMapper);
        ReflectionTestUtils.setField(service, "productImageMapper", productImageMapper);
        ReflectionTestUtils.setField(service, "notificationService", notificationService);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(24L), org.mockito.ArgumentMatchers.eq(TimeUnit.HOURS))).thenReturn(true);
        ReflectionTestUtils.setField(service, "notificationRedis", redis);
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

    @Test
    @SuppressWarnings("unchecked")
    void myFavoritesKeepRecordsAndAttachProductImages() {
        Favorite firstFavorite = new Favorite();
        firstFavorite.setId(100L);
        firstFavorite.setProductId(10L);
        firstFavorite.setCreateTime(new Date(1000L));
        Favorite secondFavorite = new Favorite();
        secondFavorite.setId(101L);
        secondFavorite.setProductId(11L);
        secondFavorite.setCreateTime(new Date(2000L));

        Page<Favorite> page = new Page<>(1, 12);
        page.setRecords(Arrays.asList(firstFavorite, secondFavorite));
        page.setTotal(2);

        Product product = new Product();
        product.setId(10L);
        product.setTitle("已存在商品");
        ProductImage image = new ProductImage();
        image.setProductId(10L);
        image.setUrl("/uploads/1.jpg");

        List<Long> productIds = Arrays.asList(10L, 11L);
        when(favoriteMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
        when(productMapper.selectBatchIds(productIds)).thenReturn(Collections.singletonList(product));
        when(productImageMapper.selectByProductIds(productIds)).thenReturn(Collections.singletonList(image));

        Result result = service.getMyFavorites(1L, 1, 12);

        assertThat(result.getCode()).isEqualTo(200);
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
        assertThat(records).hasSize(2);
        assertThat(records.get(0).get("favoriteId")).isEqualTo(100L);

        Product attachedProduct = (Product) records.get(0).get("product");
        assertThat(attachedProduct.getImages()).containsExactly(image);
        assertThat(attachedProduct.getImageUrls()).isNull();

        assertThat(records.get(1).get("favoriteId")).isEqualTo(101L);
        assertThat(records.get(1)).doesNotContainKey("product");
        assertThat(data.get("total")).isEqualTo(2L);
    }

    private Product product(Long id, Integer status) {
        Product product = new Product();
        product.setId(id);
        product.setStatus(status);
        product.setSellerId(99L);  // 卖家≠收藏者(1L)，触发"收藏通知"分支（通知服务已mock）
        return product;
    }
}
