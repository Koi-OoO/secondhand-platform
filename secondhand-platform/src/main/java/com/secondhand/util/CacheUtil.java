package com.secondhand.util;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 缓存工具类
 */
@Component
public class CacheUtil {

    // 日志记录器
    private static final Logger log = LoggerFactory.getLogger(CacheUtil.class);

    // 空值占位符，用于缓存穿透防护
    public static final String NULL_PLACEHOLDER = "__NULL__";
    // 空值占位符的过期时间（秒），较短时间即可
    private static final long NULL_TTL = 60;
    // 分布式锁的key前缀
    private static final String REBUILD_LOCK_PREFIX = "cache:lock:";
    // 获取锁的等待时间（秒）
    private static final long REBUILD_LOCK_WAIT = 1;
    // 锁的持有时间（秒），防止死锁
    private static final long REBUILD_LOCK_LEASE = 5;

    // 操作Redis的模板类，由Spring自动注入
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    // Redisson客户端，用于分布式锁
    @Resource
    private RedissonClient redissonClient;

    /**
     * 写入普通缓存，支持过期时间随机抖动
     * @param key 缓存键
     * @param value 缓存值
     * @param baseTTL 基础过期时间（秒）
     * @param maxJitter 最大随机抖动值（秒）
     */
    public void set(String key, Object value, long baseTTL, long maxJitter) {
        // 计算实际过期时间 = 基础时间 + 随机抖动值（0到maxJitter之间的随机数）
        long ttl = baseTTL + (maxJitter > 0 ? (long) (Math.random() * maxJitter) : 0);
        // 存入Redis，并设置过期时间
        redisTemplate.opsForValue().set(key, value, ttl, TimeUnit.SECONDS);
    }

    /**
     * 根据key获取缓存值
     * @param key 缓存键
     * @return 缓存值
     */
    public Object get(String key) {
        // 直接调用RedisTemplate获取
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 删除指定key的缓存
     * @param key 缓存键
     */
    public void delete(String key) {
        // 删除Redis中的key
        redisTemplate.delete(key);
    }

    /**
     * 设置空值占位符，防止缓存穿透
     * @param key 缓存键
     */
    public void setNull(String key) {
        // 存入空值占位符，并设置较短的过期时间
        redisTemplate.opsForValue().set(key, NULL_PLACEHOLDER, NULL_TTL, TimeUnit.SECONDS);
    }

    /**
     * 判断缓存值是否为空值占位符
     * @param value 缓存值
     * @return 是否是占位符
     */
    public static boolean isNullPlaceholder(Object value) {
        // 用equals判断是否等于空值占位符常量
        return NULL_PLACEHOLDER.equals(value);
    }

    /**
     * 写入带逻辑过期时间的缓存
     * @param key 缓存键
     * @param value 缓存值
     * @param logicalTTL 逻辑过期时间（秒）
     * @param physicalTTL Redis物理过期时间（秒）
     */
    public void setWithLogicalExpire(String key, Object value, long logicalTTL, long physicalTTL) {
        // 计算逻辑过期的时间戳 = 当前时间 + 逻辑过期时长
        long expireAt = System.currentTimeMillis() + logicalTTL * 1000;
        // 创建CacheEntry对象，包装数据和过期时间戳
        CacheEntry entry = new CacheEntry(value, expireAt);
        // 存入Redis，物理过期时间要比逻辑过期时间长
        redisTemplate.opsForValue().set(key, entry, physicalTTL, TimeUnit.SECONDS);
    }

    /**
     * 获取逻辑过期缓存，已过期返回null
     * @param key 缓存键
     * @return 有效缓存值，逻辑过期或不存在返回null
     */
    public Object getWithLogicalExpire(String key) {
        // 从Redis中获取缓存对象
        Object obj = redisTemplate.opsForValue().get(key);
        // 缓存不存在，返回null
        if (obj == null) {
            return null;
        }
        // 如果是CacheEntry类型，需要判断逻辑过期
        if (obj instanceof CacheEntry) {
            CacheEntry entry = (CacheEntry) obj;
            // 判断是否逻辑过期，过期返回null
            if (entry.isLogicExpired()) {
                return null;
            }
            // 未过期返回实际数据
            return entry.getData();
        }
        // 非CacheEntry类型，直接返回
        return obj;
    }

    /**
     * 即使逻辑过期也返回旧值（兜底用）
     * @param key 缓存键
     * @return 缓存中的旧值
     */
    public Object getEvenIfExpired(String key) {
        // 从Redis获取缓存对象
        Object obj = redisTemplate.opsForValue().get(key);
        // 不存在返回null
        if (obj == null) {
            return null;
        }
        // 如果是CacheEntry，返回其中的数据（不判断是否过期）
        if (obj instanceof CacheEntry) {
            return ((CacheEntry) obj).getData();
        }
        // 普通对象直接返回
        return obj;
    }

    /**
     * 通用缓存回源模板：先查缓存，未命中则加载数据并写入缓存
     * @param key 缓存键
     * @param baseTTL 基础过期时间
     * @param maxJitter 最大随机抖动
     * @param loader 数据加载器
     * @param <T> 返回类型
     * @return 缓存或加载的数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrLoad(String key, long baseTTL, long maxJitter, Supplier<T> loader) {
        // 先从Redis获取缓存
        Object cached = redisTemplate.opsForValue().get(key);
        // 缓存命中
        if (cached != null) {
            // 如果是空值占位符，返回null
            if (isNullPlaceholder(cached)) {
                return null;
            }
            // 返回真实数据
            return (T) cached;
        }

        // 缓存未命中，调用加载器从数据库获取数据
        T data = loader.get();
        // 数据为空，存入空值占位符防止穿透
        if (data == null) {
            setNull(key);
            log.debug("Cache miss and DB returned null, key={}", key);
        } else {
            // 数据不为空，存入缓存
            set(key, data, baseTTL, maxJitter);
        }
        return data;
    }

    /**
     * 防穿透查询：先读缓存，未命中回源，空值写占位符
     * @param keyPrefix key前缀
     * @param id 业务id
     * @param type 返回类型
     * @param dbFallback 数据库查询函数
     * @param baseTTL 基础过期时间
     * @param maxJitter 最大随机抖动
     * @param <ID> id类型
     * @param <T> 返回类型
     * @return 查询结果
     */
    public <ID, T> T queryWithPassThrough(String keyPrefix, ID id, Class<T> type,
                                          Function<ID, T> dbFallback,
                                          long baseTTL, long maxJitter) {
        // 拼接完整的缓存key
        String key = keyPrefix + id;
        // 从Redis获取缓存
        Object cached = redisTemplate.opsForValue().get(key);
        // 缓存命中
        if (cached != null) {
            // 空值占位符返回null，否则返回真实数据
            return isNullPlaceholder(cached) ? null : type.cast(cached);
        }
        // 缓存未命中，从数据库查询
        T data = dbFallback.apply(id);
        // 数据库无数据，设置空值占位符
        if (data == null) {
            setNull(key);
            return null;
        }
        // 有数据，存入缓存
        set(key, data, baseTTL, maxJitter);
        return data;
    }

    /**
     * 防击穿查询（互斥锁版）：只让一个线程回源重建缓存
     * @param keyPrefix key前缀
     * @param id 业务id
     * @param type 返回类型
     * @param dbFallback 数据库查询函数
     * @param baseTTL 过期时间
     * @param <ID> id类型
     * @param <T> 返回类型
     * @return 查询结果
     */
    public <ID, T> T queryWithMutex(String keyPrefix, ID id, Class<T> type,
                                    Function<ID, T> dbFallback, long baseTTL) {
        // 拼接缓存key
        String key = keyPrefix + id;
        // 先查缓存
        Object cached = redisTemplate.opsForValue().get(key);
        // 缓存命中，直接返回
        if (cached != null) {
            return isNullPlaceholder(cached) ? null : type.cast(cached);
        }
        // 获取分布式锁
        RLock lock = redissonClient.getLock(REBUILD_LOCK_PREFIX + key);
        boolean locked = false;
        try {
            // 尝试获取锁
            locked = lock.tryLock(REBUILD_LOCK_WAIT, REBUILD_LOCK_LEASE, TimeUnit.SECONDS);
            // 没拿到锁
            if (!locked) {
                // 等待50ms后重试
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, baseTTL);
            }
            // 拿到锁后双重检查，防止其他线程已经重建了缓存
            Object recheck = redisTemplate.opsForValue().get(key);
            if (recheck != null) {
                return isNullPlaceholder(recheck) ? null : type.cast(recheck);
            }
            // 从数据库查询数据
            T data = dbFallback.apply(id);
            // 数据为空，设置空值占位符
            if (data == null) {
                setNull(key);
                return null;
            }
            // 存入缓存
            set(key, data, baseTTL, 0);
            return data;
        } catch (InterruptedException ex) {
            // 中断异常，恢复中断状态
            Thread.currentThread().interrupt();
            return null;
        } finally {
            // 释放锁（只有当前线程持有锁时才释放）
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 防击穿查询（逻辑过期版）：逻辑过期后异步重建，其他线程先用旧值
     * @param keyPrefix key前缀
     * @param id 业务id
     * @param type 返回类型
     * @param dbFallback 数据库查询函数
     * @param logicalTTL 逻辑过期时间
     * @param physicalTTL 物理过期时间
     * @param <ID> id类型
     * @param <T> 返回类型
     * @return 查询结果
     */
    public <ID, T> T queryWithLogicalExpire(String keyPrefix, ID id, Class<T> type,
                                            Function<ID, T> dbFallback,
                                            long logicalTTL, long physicalTTL) {
        // 拼接缓存key
        String key = keyPrefix + id;
        // 获取逻辑过期缓存（未过期才返回数据）
        Object current = getWithLogicalExpire(key);
        // 缓存有效，直接返回
        if (current != null) {
            return isNullPlaceholder(current) ? null : type.cast(current);
        }
        // 缓存逻辑过期或不存在，尝试获取锁去重建
        RLock lock = redissonClient.getLock(REBUILD_LOCK_PREFIX + key);
        boolean locked = false;
        try {
            // 尝试获取锁
            locked = lock.tryLock(REBUILD_LOCK_WAIT, REBUILD_LOCK_LEASE, TimeUnit.SECONDS);
            // 没拿到锁
            if (!locked) {
                // 获取旧值（即使过期也返回）做降级兜底
                Object stale = getEvenIfExpired(key);
                if (stale != null) {
                    return isNullPlaceholder(stale) ? null : type.cast(stale);
                }
                // 没有旧值，等待100ms后重试
                Thread.sleep(100);
                return queryWithLogicalExpire(keyPrefix, id, type, dbFallback, logicalTTL, physicalTTL);
            }
            // 拿到锁后双重检查
            Object recheck = getWithLogicalExpire(key);
            if (recheck != null) {
                return isNullPlaceholder(recheck) ? null : type.cast(recheck);
            }
            // 从数据库查询数据
            T data = dbFallback.apply(id);
            // 数据为空，设置空值占位符
            if (data == null) {
                setNull(key);
                return null;
            }
            // 写入逻辑过期缓存
            setWithLogicalExpire(key, data, logicalTTL, physicalTTL);
            return data;
        } catch (InterruptedException ex) {
            // 中断异常，恢复中断状态
            Thread.currentThread().interrupt();
            return null;
        } finally {
            // 释放锁
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 缓存包装类，用于存储数据和逻辑过期时间
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CacheEntry {
        private Object data;      // 业务数据
        private long expireAt;    // 逻辑过期时间戳

        // 无参构造，供Jackson反序列化使用
        public CacheEntry() {
        }

        // 有参构造
        public CacheEntry(Object data, long expireAt) {
            this.data = data;
            this.expireAt = expireAt;
        }

        // 判断是否逻辑过期
        public boolean isLogicExpired() {
            return System.currentTimeMillis() > expireAt;
        }

        // getter和setter方法
        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
        }

        public long getExpireAt() {
            return expireAt;
        }

        public void setExpireAt(long expireAt) {
            this.expireAt = expireAt;
        }
    }
}