package com.secondhand.util;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CacheUtilTest {

    private RedisTemplate<String, Object> redisTemplate;
    private ValueOperations<String, Object> valueOps;
    private RedissonClient redissonClient;
    private RLock lock;
    private CacheUtil cacheUtil;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        redissonClient = mock(RedissonClient.class);
        lock = mock(RLock.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        cacheUtil = new CacheUtil();
        ReflectionTestUtils.setField(cacheUtil, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(cacheUtil, "redissonClient", redissonClient);
    }

    @Test
    void queryWithPassThroughReturnsCachedValueWithoutHittingDb() {
        when(valueOps.get("p:1")).thenReturn("cached");
        AtomicBoolean loaderCalled = new AtomicBoolean(false);

        String result = cacheUtil.queryWithPassThrough("p:", 1, String.class, id -> {
            loaderCalled.set(true);
            return "db";
        }, 100, 0);

        assertThat(result).isEqualTo("cached");
        assertThat(loaderCalled.get()).isFalse();
    }

    @Test
    void queryWithPassThroughLoadsFromDbOnMissAndCaches() {
        when(valueOps.get("p:2")).thenReturn(null);

        String result = cacheUtil.queryWithPassThrough("p:", 2, String.class, id -> "db-value", 100, 0);

        assertThat(result).isEqualTo("db-value");
        verify(valueOps).set(eq("p:2"), eq("db-value"), anyLong(), any());
    }

    @Test
    void queryWithPassThroughWritesNullPlaceholderWhenDbReturnsNull() {
        when(valueOps.get("p:3")).thenReturn(null);

        String result = cacheUtil.queryWithPassThrough("p:", 3, String.class, id -> null, 100, 0);

        assertThat(result).isNull();
        verify(valueOps).set(eq("p:3"), eq(CacheUtil.NULL_PLACEHOLDER), anyLong(), any());
    }

    @Test
    void queryWithMutexRebuildsUnderLockOnMiss() throws InterruptedException {
        when(valueOps.get("m:1")).thenReturn(null);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        String result = cacheUtil.queryWithMutex("m:", 1, String.class, id -> "rebuilt", 100);

        assertThat(result).isEqualTo("rebuilt");
        verify(lock).unlock();
    }

    @Test
    void queryWithLogicalExpireReturnsLiveValue() {
        CacheUtil.CacheEntry entry = new CacheUtil.CacheEntry("hot", System.currentTimeMillis() + 60_000);
        when(valueOps.get("l:1")).thenReturn(entry);

        String result = cacheUtil.queryWithLogicalExpire("l:", 1, String.class, id -> "db", 60, 600);

        assertThat(result).isEqualTo("hot");
    }

    @Test
    void queryWithLogicalExpireRebuildsWhenLogicallyExpired() throws InterruptedException {
        CacheUtil.CacheEntry expired = new CacheUtil.CacheEntry("stale", System.currentTimeMillis() - 1_000);
        when(valueOps.get("l:2")).thenReturn(expired);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        String result = cacheUtil.queryWithLogicalExpire("l:", 2, String.class, id -> "fresh", 60, 600);

        assertThat(result).isEqualTo("fresh");
    }

    @Test
    void cacheEntryDeserializesLegacyLogicExpiredField() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        String json = "{\"@class\":\"com.secondhand.util.CacheUtil$CacheEntry\","
                + "\"data\":\"cached-product\","
                + "\"expireAt\":9999999999999,"
                + "\"logicExpired\":false}";

        Object value = serializer.deserialize(json.getBytes());

        assertThat(value).isInstanceOf(CacheUtil.CacheEntry.class);
        CacheUtil.CacheEntry entry = (CacheUtil.CacheEntry) value;
        assertThat(entry.getData()).isEqualTo("cached-product");
        assertThat(entry.getExpireAt()).isEqualTo(9999999999999L);
    }
}
