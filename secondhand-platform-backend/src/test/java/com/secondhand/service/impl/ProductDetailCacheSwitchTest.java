package com.secondhand.service.impl;

import com.secondhand.entity.Product;
import com.secondhand.entity.ProductImage;
import com.secondhand.mapper.CategoryMapper;
import com.secondhand.mapper.ProductImageMapper;
import com.secondhand.mapper.ProductMapper;
import com.secondhand.util.CacheUtil;
import com.secondhand.util.Result;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductDetailCacheSwitchTest {
    private final ProductMapper products = mock(ProductMapper.class);
    private final ProductImageMapper images = mock(ProductImageMapper.class);
    private final CacheUtil cache = mock(CacheUtil.class);
    private final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    private final ValueOperations<String, Object> values = mock(ValueOperations.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(ProductMapper.class, () -> products)
            .withBean(ProductImageMapper.class, () -> images)
            .withBean(CategoryMapper.class, () -> mock(CategoryMapper.class))
            .withBean(CacheUtil.class, () -> cache)
            .withBean(com.secondhand.websocket.ProductChangePublisher.class, () -> mock(com.secondhand.websocket.ProductChangePublisher.class))
            .withBean(RedisTemplate.class, () -> redis)
            .withBean(RedissonClient.class, () -> mock(RedissonClient.class))
            .withUserConfiguration(ProductServiceImpl.class);

    @Test
    void defaultAndExplicitEnabledUseCacheWithoutDatabaseQuery() {
        Product product = product(1);
        when(redis.opsForValue()).thenReturn(values);
        when(cache.<Long, Product>queryWithLogicalExpire(
                eq("product:detail:"), eq(40L), eq(Product.class), any(), anyLong(), anyLong()))
                .thenReturn(product);
        for (String[] properties : List.of(new String[0],
                new String[]{"app.cache.product-detail.enabled=true"})) {
            runner.withPropertyValues(properties).run(context -> {
                Result result = context.getBean(ProductServiceImpl.class).getDetail(40L);
                assertThat(result.getData()).isSameAs(product);
                assertThat(result.getCode()).isEqualTo(200);
            });
        }
        verifyNoInteractions(products, images);
        verify(values, times(2)).increment("product:views:40", 1);
    }

    @Test
    void disabledAlwaysLoadsDatabaseAndImagesButStillRecordsViews() {
        Product product = product(1);
        ProductImage image = new ProductImage();
        image.setUrl("/uploads/example.jpg");
        when(products.selectByIdWithDetails(40L)).thenReturn(product);
        when(images.selectByProductId(40L)).thenReturn(List.of(image));
        when(redis.opsForValue()).thenReturn(values);
        runner.withPropertyValues("app.cache.product-detail.enabled=false").run(context -> {
            ProductServiceImpl service = context.getBean(ProductServiceImpl.class);
            for (int i = 0; i < 2; i++) {
                Result result = service.getDetail(40L);
                assertThat(result.getCode()).isEqualTo(200);
                assertThat(result.getData()).isSameAs(product);
                assertThat(product.getImages()).containsExactly(image);
            }
        });
        verify(products, times(2)).selectByIdWithDetails(40L);
        verify(images, times(2)).selectByProductId(40L);
        verify(values, times(2)).increment("product:views:40", 1);
        verifyNoInteractions(cache);
    }

    @Test
    void disabledAiUsesSameLoaderWithoutRecordingViewsAndStillHidesUnavailableProducts() {
        Product product = product(1);
        when(products.selectByIdWithDetails(40L)).thenReturn(product);
        when(images.selectByProductId(40L)).thenReturn(List.of());
        runner.withPropertyValues("app.cache.product-detail.enabled=false").run(context -> {
            ProductServiceImpl service = context.getBean(ProductServiceImpl.class);
            // Spring 初始化 RedisTemplate mock 时也会调用生命周期方法，不计入业务访问。
            clearInvocations(redis);
            assertThat(service.getDetailForAi(40L).getData()).isSameAs(product);
            product.setStatus(2);
            assertThat(service.getDetailForAi(40L).getMessage()).isEqualTo("商品不存在");
            product.setStatus(4);
            assertThat(service.getDetail(40L).getMessage()).isEqualTo("商品不存在");
        });
        verifyNoInteractions(cache, redis);
    }

    @Test
    void disabledPreservesMissingProductAndInvalidIdChecks() {
        runner.withPropertyValues("app.cache.product-detail.enabled=false").run(context -> {
            ProductServiceImpl service = context.getBean(ProductServiceImpl.class);
            clearInvocations(redis);
            assertThat(service.getDetail(40L).getMessage()).isEqualTo("商品不存在");
            assertThat(service.getDetail(null).getMessage()).isEqualTo("商品 ID 无效");
            assertThat(service.getDetail(0L).getMessage()).isEqualTo("商品 ID 无效");
        });
        verify(products).selectByIdWithDetails(40L);
        verifyNoInteractions(images, cache, redis);
    }

    private Product product(int status) {
        Product product = new Product();
        product.setId(40L);
        product.setStatus(status);
        return product;
    }
}
