package com.secondhand.service.impl;

import com.secondhand.entity.Product;
import com.secondhand.mapper.FavoriteMapper;
import com.secondhand.mapper.ProductMapper;
import com.secondhand.mapper.ProductImageMapper;
import com.secondhand.service.NotificationService;
import java.util.concurrent.TimeUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FavoriteNotificationPolicyTest {
    final FavoriteMapper favorites = mock(FavoriteMapper.class);
    final ProductMapper products = mock(ProductMapper.class);
    final NotificationService notifications = mock(NotificationService.class);
    final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    final ValueOperations<String, String> values = mock(ValueOperations.class);
    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(FavoriteMapper.class, () -> favorites)
            .withBean(ProductMapper.class, () -> products)
            .withBean(ProductImageMapper.class, () -> mock(ProductImageMapper.class))
            .withBean(NotificationService.class, () -> notifications)
            .withBean(StringRedisTemplate.class, () -> redis)
            .withUserConfiguration(FavoriteServiceImpl.class);

    FavoriteNotificationPolicyTest() {
        Product p = new Product();p.setId(10L);p.setSellerId(99L);p.setStatus(1);p.setTitle("台灯");
        when(products.selectById(10L)).thenReturn(p);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(),anyString(),eq(24L),eq(TimeUnit.HOURS))).thenReturn(true);
    }

    @Test
    void repeatedFavoriteAfterCancellationKeepsHistoryButDoesNotNotifyAgainWithinWindow() {
        when(values.setIfAbsent(eq("notification:favorite:1:10"),anyString(),eq(24L),eq(TimeUnit.HOURS)))
                .thenReturn(true,false);
        when(favorites.delete(any())).thenReturn(1);
        runner.run(context -> {
            FavoriteServiceImpl service=context.getBean(FavoriteServiceImpl.class);
            assertThat(service.addFavorite(1L,10L).getCode()).isEqualTo(200);
            assertThat(service.removeFavorite(1L,10L).getCode()).isEqualTo(200);
            assertThat(service.addFavorite(1L,10L).getCode()).isEqualTo(200);
        });
        verify(notifications).notifyFavorite(99L,1L,10L,"台灯");
        verifyNoMoreInteractions(notifications);
        verify(products,times(2)).incrementLikeCount(10L,1);
        verify(products).incrementLikeCount(10L,-1);
    }

    @Test
    void anotherUserAndAnotherProductHaveIndependentWindows() {
        Product p=new Product();p.setId(11L);p.setSellerId(99L);p.setStatus(1);p.setTitle("书包");
        when(products.selectById(11L)).thenReturn(p);
        runner.run(context -> {
            FavoriteServiceImpl service=context.getBean(FavoriteServiceImpl.class);
            service.addFavorite(1L,10L);service.addFavorite(2L,10L);service.addFavorite(1L,11L);
        });
        verify(values).setIfAbsent(eq("notification:favorite:1:10"),anyString(),eq(24L),eq(TimeUnit.HOURS));
        verify(values).setIfAbsent(eq("notification:favorite:2:10"),anyString(),eq(24L),eq(TimeUnit.HOURS));
        verify(values).setIfAbsent(eq("notification:favorite:1:11"),anyString(),eq(24L),eq(TimeUnit.HOURS));
        verify(notifications,times(3)).notifyFavorite(eq(99L),anyLong(),anyLong(),anyString());
    }

    @Test
    void existingFavoriteAndSelfFavoriteDoNotNotify() {
        when(favorites.countByUserAndProduct(1L,10L)).thenReturn(1);
        runner.run(context -> {
            FavoriteServiceImpl service=context.getBean(FavoriteServiceImpl.class);
            service.addFavorite(1L,10L);service.addFavorite(99L,10L);
        });
        verifyNoInteractions(values,notifications);
    }

    @Test
    void cancellationDoesNotDeleteHistoryOrCooldown() {
        when(favorites.delete(any())).thenReturn(1);
        runner.run(context -> context.getBean(FavoriteServiceImpl.class).removeFavorite(1L,10L));
        verifyNoInteractions(notifications,values);
        verify(redis,never()).delete(anyString());
    }

    @Test
    void redisFailureDoesNotRollBackFavorite() {
        when(values.setIfAbsent(anyString(),anyString(),eq(24L),eq(TimeUnit.HOURS)))
                .thenThrow(new IllegalStateException("Redis unavailable"));
        runner.run(context -> assertThat(context.getBean(FavoriteServiceImpl.class).addFavorite(1L,10L).getCode()).isEqualTo(200));
        verifyNoInteractions(notifications);
        verify(products).incrementLikeCount(10L,1);
    }

    @Test
    void notificationCanBeSentAgainAfterCooldownKeyExpires() {
        when(values.setIfAbsent(anyString(),anyString(),eq(24L),eq(TimeUnit.HOURS)))
                .thenReturn(true,false,true);
        runner.run(context -> {
            FavoriteServiceImpl service=context.getBean(FavoriteServiceImpl.class);
            service.addFavorite(1L,10L);service.addFavorite(1L,10L);service.addFavorite(1L,10L);
        });
        verify(notifications,times(2)).notifyFavorite(99L,1L,10L,"台灯");
    }

    @ParameterizedTest
    @ValueSource(ints = {TransactionSynchronization.STATUS_COMMITTED, TransactionSynchronization.STATUS_ROLLED_BACK})
    void onlyRollbackReleasesOwnedCooldownMarker(int status) {
        runner.run(context -> {
            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                context.getBean(FavoriteServiceImpl.class).addFavorite(1L,10L);
                List<TransactionSynchronization> callbacks=TransactionSynchronizationManager.getSynchronizations();
                assertThat(callbacks).hasSize(1);
                callbacks.forEach(callback -> callback.afterCompletion(status));
                if(status==TransactionSynchronization.STATUS_ROLLED_BACK) {
                    verify(redis).execute(any(DefaultRedisScript.class),eq(List.of("notification:favorite:1:10")),anyString());
                } else {
                    verify(redis,never()).execute(any(DefaultRedisScript.class),anyList(),anyString());
                }
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
                TransactionSynchronizationManager.setActualTransactionActive(false);
            }
        });
    }
}
