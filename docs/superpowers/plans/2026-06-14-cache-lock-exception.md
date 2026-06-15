# Cache Utility, Order Lock, and Global Exception Handler Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add three interview-relevant robustness features to the existing Spring Boot backend — a global exception handler, a Redisson distributed lock on order creation/stock, and a reusable generic cache utility that solves penetration/breakdown/avalanche.

**Architecture:** Three independent changes, each self-contained and testable on its own. (1) A `@RestControllerAdvice` that maps any uncaught exception to the unified `Result` JSON. (2) `OrderServiceImpl.createOrder` and the stock-restore path acquire a per-product Redisson lock so concurrent buyers cannot oversell; the lock model matches the existing `offShelf`/`onShelf` code. (3) `CacheUtil` gains three generic methods (`queryWithPassThrough`, `queryWithMutex`, `queryWithLogicalExpire`) and `ProductServiceImpl.getDetail` collapses to a single call into it.

**Tech Stack:** Spring Boot 2.7, MyBatis-Plus, Redis (`RedisTemplate`), Redisson, JUnit 5 + Mockito + AssertJ, Maven (Java 17).

---

## Conventions for this plan

- **Working directory for all commands:** `D:/my-project/secondhand-platform`
- **Run one test class:** `mvn -q -Dtest=ClassName test`
- **Run one test method:** `mvn -q -Dtest='ClassName#methodName' test`
- **Run everything:** `mvn -q test` (expected tail: `BUILD SUCCESS`)
- **Git:** This project is **not** a git repo yet. Either run `git init` in `D:/my-project` first (recommended — a GitHub repo is a resume asset), or treat each **Commit** step as a checkpoint and skip the `git` commands. Commit steps are written assuming git is initialized.

## File Structure

| File | Responsibility | Action |
|---|---|---|
| `src/main/java/com/secondhand/handler/GlobalExceptionHandler.java` | Map exceptions → unified `Result` | Create |
| `src/test/java/com/secondhand/handler/GlobalExceptionHandlerTest.java` | Unit-test the handler | Create |
| `src/main/java/com/secondhand/service/impl/OrderServiceImpl.java` | Lock `createOrder` + `restoreProductStock` | Modify |
| `src/test/java/com/secondhand/service/impl/OrderServiceImplTest.java` | Lock mocks + lock-fail test | Modify |
| `src/main/java/com/secondhand/util/CacheUtil.java` | Add 3 generic cache methods + `RedissonClient` | Modify |
| `src/test/java/com/secondhand/util/CacheUtilTest.java` | Unit-test the 3 methods | Modify |
| `src/main/java/com/secondhand/service/impl/ProductServiceImpl.java` | `getDetail` uses `queryWithLogicalExpire` | Modify |
| `src/test/java/com/secondhand/service/impl/ProductServiceImplTest.java` | Add `getDetail` tests | Modify |

Tasks are ordered easiest-first (independent → independent → refactor) for clean incremental execution.

---

## Task 1: Global Exception Handler

**Files:**
- Create: `src/main/java/com/secondhand/handler/GlobalExceptionHandler.java`
- Test: `src/test/java/com/secondhand/handler/GlobalExceptionHandlerTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/secondhand/handler/GlobalExceptionHandlerTest.java`:

```java
package com.secondhand.handler;

import com.secondhand.util.Result;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.MissingServletRequestParameterException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleExceptionReturnsUnifiedErrorResult() {
        Result result = handler.handleException(new RuntimeException("boom"));

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMessage()).isEqualTo("服务器繁忙，请稍后再试");
        assertThat(result.getData()).isNull();
    }

    @Test
    void handleMissingParamMentionsParameterName() {
        Result result = handler.handleMissingParam(
                new MissingServletRequestParameterException("productId", "Long"));

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMessage()).contains("productId");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=GlobalExceptionHandlerTest test`
Expected: FAIL — compilation error, `GlobalExceptionHandler` does not exist.

- [ ] **Step 3: Create the handler**

Create `src/main/java/com/secondhand/handler/GlobalExceptionHandler.java`:

```java
package com.secondhand.handler;

import com.secondhand.util.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理器。
 * 统一把未捕获异常转换成项目的 Result 结构，避免直接暴露 Spring 默认错误页。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 请求参数类型不匹配，例如路径或查询参数应为数字却传了非数字。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return Result.error("参数格式不正确：" + ex.getName());
    }

    /**
     * 缺少必填请求参数。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result handleMissingParam(MissingServletRequestParameterException ex) {
        return Result.error("缺少必填参数：" + ex.getParameterName());
    }

    /**
     * 请求体无法解析（如 JSON 格式错误）。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result handleNotReadable(HttpMessageNotReadableException ex) {
        return Result.error("请求体格式错误");
    }

    /**
     * 兜底处理所有未被专门处理的异常，记录日志但不向客户端泄露堆栈。
     */
    @ExceptionHandler(Exception.class)
    public Result handleException(Exception ex) {
        log.error("未捕获异常", ex);
        return Result.error("服务器繁忙，请稍后再试");
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=GlobalExceptionHandlerTest test`
Expected: PASS — `Tests run: 2, Failures: 0`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/secondhand/handler/GlobalExceptionHandler.java \
        src/test/java/com/secondhand/handler/GlobalExceptionHandlerTest.java
git commit -m "feat: add global exception handler returning unified Result"
```

---

## Task 2: Redisson Lock on Order Creation and Stock Restore

**Files:**
- Modify: `src/main/java/com/secondhand/service/impl/OrderServiceImpl.java`
- Test: `src/test/java/com/secondhand/service/impl/OrderServiceImplTest.java`

This task keeps the existing in-memory stock logic unchanged and only wraps it in a per-product lock, so all current assertions stay valid once the lock is mocked.

- [ ] **Step 1: Add lock mocks to the test setup and write the failing lock-fail test**

In `OrderServiceImplTest.java`, add imports:

```java
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import java.util.concurrent.TimeUnit;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
```

Add two fields next to the other mocks:

```java
    private RedissonClient redissonClient;
    private RLock rLock;
```

Change the `setUp` signature to `void setUp() throws InterruptedException` and add, before the `ReflectionTestUtils` block:

```java
        redissonClient = mock(RedissonClient.class);
        rLock = mock(RLock.class);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
```

Add to the `ReflectionTestUtils` block:

```java
        ReflectionTestUtils.setField(service, "redissonClient", redissonClient);
```

Add this new test method:

```java
    @Test
    void createOrderReturnsErrorWhenLockNotAcquired() throws InterruptedException {
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        Result result = invokeCreateOrder(20L, 40L, 1, "Test address");

        assertThat(result.getCode()).isEqualTo(500);
        verify(orderMapper, never()).insert(any(Order.class));
        verify(productMapper, never()).updateById(any(Product.class));
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=OrderServiceImplTest test`
Expected: FAIL — compilation error, `OrderServiceImpl` has no `redissonClient` field, and `createOrder` does not yet branch on `tryLock`.

- [ ] **Step 3: Add the field, constants, and imports to `OrderServiceImpl`**

Add imports:

```java
import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
```

Add the injected client next to the other `@Autowired` fields:

```java
    @Autowired
    private RedissonClient redissonClient;
```

Add constants near the top of the class body:

```java
    private static final String ORDER_LOCK_KEY = "order:lock:product:";
    private static final long LOCK_WAIT_SECONDS = 3;
    private static final long LOCK_LEASE_SECONDS = 10;
```

- [ ] **Step 4: Replace `createOrder` with a lock wrapper delegating to `doCreateOrder`**

Replace the entire existing `createOrder` method with:

```java
    @Override
    @Transactional
    public Result createOrder(Long buyerId, Long productId, Integer quantity, String address) {
        if (quantity == null || quantity <= 0) {
            return Result.error("购买数量必须大于 0");
        }
        if (address == null || address.trim().isEmpty()) {
            return Result.error("请填写收货地址");
        }
        if (productId == null || productId <= 0) {
            return Result.error("商品 ID 无效");
        }

        // 同一商品的下单串行化，避免并发把库存扣成负数。
        RLock lock = redissonClient.getLock(ORDER_LOCK_KEY + productId);
        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                return Result.error("下单的人有点多，请稍后再试");
            }
            return doCreateOrder(buyerId, productId, quantity, address);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Result.error("系统繁忙，请重试");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 在持有商品锁的前提下执行真正的下单逻辑：校验、写订单、扣库存。
     */
    private Result doCreateOrder(Long buyerId, Long productId, Integer quantity, String address) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            return Result.error("商品不存在");
        }
        if (product.getStatus() != 1) {
            return Result.error("商品已下架或已售罄");
        }
        if (product.getSellerId().equals(buyerId)) {
            return Result.error("不能购买自己发布的商品");
        }
        if (product.getStock() == null || product.getStock() < quantity) {
            return Result.error("库存不足");
        }

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getBuyerId, buyerId)
                .eq(Order::getProductId, productId)
                .lt(Order::getStatus, 3);
        // 同一买家对同一商品只保留一个未完成订单，避免重复占用库存。
        if (orderMapper.selectCount(wrapper) > 0) {
            return Result.error("您已有该商品的未完成订单");
        }

        String orderNo = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
                + String.format("%06d", new Random().nextInt(999999));
        BigDecimal productAmount = product.getPrice().multiply(BigDecimal.valueOf(quantity.longValue()));

        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setBuyerId(buyerId);
        order.setSellerId(product.getSellerId());
        order.setProductId(productId);
        order.setQuantity(quantity);
        order.setProductAmount(productAmount);
        order.setFreight(BigDecimal.ZERO);
        order.setTotalAmount(productAmount);
        order.setAddress(address.trim());
        order.setStatus(1);
        order.setCreateTime(new Date());
        orderMapper.insert(order);

        product.setStock(product.getStock() - quantity);
        product.setStatus(product.getStock() == 0 ? 3 : 1);
        product.setUpdateTime(new Date());
        productMapper.updateById(product);
        redisTemplate.delete("product:detail:" + productId);

        log.info("Order created: orderNo={} buyerId={} productId={} quantity={}",
                orderNo, buyerId, productId, quantity);
        return Result.success("下单成功");
    }
```

- [ ] **Step 5: Wrap `restoreProductStock` in the same lock**

Replace the entire existing `restoreProductStock` method with:

```java
    /**
     * 在订单取消或拒绝发货后回补商品库存，并刷新商品详情缓存。
     * 复用下单的商品锁，保证同一商品的库存增减串行执行。
     */
    private void restoreProductStock(Order order) {
        RLock lock = redissonClient.getLock(ORDER_LOCK_KEY + order.getProductId());
        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                // 拿不到锁时仍回补，避免库存被永久吞掉，但记录告警以便排查。
                log.warn("Restore stock without lock (busy): orderNo={} productId={}",
                        order.getOrderNo(), order.getProductId());
            }
            Product product = productMapper.selectById(order.getProductId());
            if (product == null) {
                return;
            }
            int restoreQuantity = order.getQuantity() == null ? 1 : order.getQuantity();
            int currentStock = product.getStock() == null ? 0 : product.getStock();
            product.setStock(currentStock + restoreQuantity);
            if (product.getStock() > 0 && product.getStatus() == 3) {
                product.setStatus(1);
            }
            product.setUpdateTime(new Date());
            productMapper.updateById(product);
            redisTemplate.delete("product:detail:" + product.getId());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
```

- [ ] **Step 6: Run the full order test class to verify it passes**

Run: `mvn -q -Dtest=OrderServiceImplTest test`
Expected: PASS — all prior tests plus `createOrderReturnsErrorWhenLockNotAcquired`. The existing `createOrderPersistsQuantityAndDeductsStock`, `cancelOrderRestoresStockUsingOrderQuantity`, and `rejectOrderRecordsSellerRejectNoticeForBuyerAndRestoresStock` still pass because the in-memory stock logic is unchanged and the lock is mocked to grant.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/secondhand/service/impl/OrderServiceImpl.java \
        src/test/java/com/secondhand/service/impl/OrderServiceImplTest.java
git commit -m "feat: serialize order creation and stock restore with a Redisson lock"
```

---

## Task 3: Generic Cache Utility + `getDetail` Refactor

**Files:**
- Modify: `src/main/java/com/secondhand/util/CacheUtil.java`
- Test: `src/test/java/com/secondhand/util/CacheUtilTest.java`
- Modify: `src/main/java/com/secondhand/service/impl/ProductServiceImpl.java`
- Test: `src/test/java/com/secondhand/service/impl/ProductServiceImplTest.java`

### Part A — add the three generic methods to `CacheUtil`

- [ ] **Step 1: Write the failing tests**

In `CacheUtilTest.java`, add imports:

```java
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
```

Add fields and setup to the class (the existing `cacheEntryDeserializesLegacyLogicExpiredField` test does not use these and keeps passing):

```java
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
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q -Dtest=CacheUtilTest test`
Expected: FAIL — compilation error, the three `queryWith...` methods and the `redissonClient` field do not exist.

- [ ] **Step 3: Add the field, imports, constants, and three methods to `CacheUtil`**

Add imports:

```java
import java.util.function.Function;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
```

Add the injected client next to the existing `redisTemplate` field:

```java
    @Resource
    private RedissonClient redissonClient;
```

Add constants near `NULL_TTL`:

```java
    private static final String REBUILD_LOCK_PREFIX = "cache:lock:";
    private static final long REBUILD_LOCK_WAIT = 1;
    private static final long REBUILD_LOCK_LEASE = 5;
```

Add the three methods to the class body (before the `CacheEntry` inner class):

```java
    /**
     * 防穿透查询：先读缓存（命中空值占位则直接返回 null），未命中回源；
     * 回源为空写空值占位，回源成功写带随机抖动 TTL 的缓存（顺带防雪崩）。
     */
    public <ID, T> T queryWithPassThrough(String keyPrefix, ID id, Class<T> type,
                                          Function<ID, T> dbFallback,
                                          long baseTTL, long maxJitter) {
        String key = keyPrefix + id;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return isNullPlaceholder(cached) ? null : type.cast(cached);
        }
        T data = dbFallback.apply(id);
        if (data == null) {
            setNull(key);
            return null;
        }
        set(key, data, baseTTL, maxJitter);
        return data;
    }

    /**
     * 防击穿查询（互斥锁版）：缓存未命中时只放一个线程拿锁回源重建，
     * 其余线程短暂等待后重试读缓存，避免大量请求同时打到数据库。
     */
    public <ID, T> T queryWithMutex(String keyPrefix, ID id, Class<T> type,
                                    Function<ID, T> dbFallback, long baseTTL) {
        String key = keyPrefix + id;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return isNullPlaceholder(cached) ? null : type.cast(cached);
        }
        RLock lock = redissonClient.getLock(REBUILD_LOCK_PREFIX + key);
        boolean locked = false;
        try {
            locked = lock.tryLock(REBUILD_LOCK_WAIT, REBUILD_LOCK_LEASE, TimeUnit.SECONDS);
            if (!locked) {
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, baseTTL);
            }
            // 双重检查：拿到锁后可能缓存已被其他线程重建。
            Object recheck = redisTemplate.opsForValue().get(key);
            if (recheck != null) {
                return isNullPlaceholder(recheck) ? null : type.cast(recheck);
            }
            T data = dbFallback.apply(id);
            if (data == null) {
                setNull(key);
                return null;
            }
            set(key, data, baseTTL, 0);
            return data;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 防击穿查询（逻辑过期版）：热点数据未逻辑过期时直接返回；逻辑过期后由
     * 一个线程持锁重建，其余线程立即拿旧值兜底、不阻塞。适合一直存在的热点 key。
     */
    public <ID, T> T queryWithLogicalExpire(String keyPrefix, ID id, Class<T> type,
                                            Function<ID, T> dbFallback,
                                            long logicalTTL, long physicalTTL) {
        String key = keyPrefix + id;
        Object current = getWithLogicalExpire(key);
        if (current != null) {
            return isNullPlaceholder(current) ? null : type.cast(current);
        }
        RLock lock = redissonClient.getLock(REBUILD_LOCK_PREFIX + key);
        boolean locked = false;
        try {
            locked = lock.tryLock(REBUILD_LOCK_WAIT, REBUILD_LOCK_LEASE, TimeUnit.SECONDS);
            if (!locked) {
                Object stale = getEvenIfExpired(key);
                if (stale != null) {
                    return isNullPlaceholder(stale) ? null : type.cast(stale);
                }
                Thread.sleep(100);
                return queryWithLogicalExpire(keyPrefix, id, type, dbFallback, logicalTTL, physicalTTL);
            }
            Object recheck = getWithLogicalExpire(key);
            if (recheck != null) {
                return isNullPlaceholder(recheck) ? null : type.cast(recheck);
            }
            T data = dbFallback.apply(id);
            if (data == null) {
                setNull(key);
                return null;
            }
            setWithLogicalExpire(key, data, logicalTTL, physicalTTL);
            return data;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q -Dtest=CacheUtilTest test`
Expected: PASS — `Tests run: 7, Failures: 0` (1 existing + 6 new).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/secondhand/util/CacheUtil.java \
        src/test/java/com/secondhand/util/CacheUtilTest.java
git commit -m "feat: add generic pass-through/mutex/logical-expire cache methods"
```

### Part B — refactor `ProductServiceImpl.getDetail` to use the utility

- [ ] **Step 6: Write the failing `getDetail` tests**

In `ProductServiceImplTest.java`, add imports:

```java
import org.springframework.data.redis.core.ValueOperations;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
```

Add these two tests:

```java
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
```

- [ ] **Step 7: Run the tests to verify they fail**

Run: `mvn -q -Dtest=ProductServiceImplTest test`
Expected: FAIL — `getDetail` still calls `cacheUtil.getWithLogicalExpire(...)` and the Redisson rebuild path, so the new `queryWithLogicalExpire` stub is never hit and behavior does not match (`getDetailReturnsProductWhenCacheUtilResolvesIt` fails).

- [ ] **Step 8: Replace `getDetail` and `loadAndCacheDetail` in `ProductServiceImpl`**

Replace the entire `getDetail` method **and** the entire `loadAndCacheDetail` method with the following (this removes the inlined lock/double-check/stale-fallback, now living in `CacheUtil`):

```java
    /**
     * 查询商品详情：缓存策略交给 CacheUtil 的逻辑过期方法处理，
     * 本方法只负责回源加载和浏览量累计等业务逻辑。
     *
     * @param productId 商品 ID
     * @return Result 商品详情结果
     */
    @Override
    public Result getDetail(Long productId) {
        if (productId == null || productId <= 0) {
            return Result.error("商品 ID 无效");
        }
        Product product = cacheUtil.queryWithLogicalExpire(
                PRODUCT_CACHE_KEY, productId, Product.class,
                this::loadProductDetail,
                DETAIL_LOGIC_TTL,
                DETAIL_PHYSIC_TTL + (long) (Math.random() * DETAIL_TTL_JITTER));
        if (product == null) {
            return Result.error("商品不存在");
        }
        incrementViewCount(productId);
        return Result.success(product);
    }

    /**
     * 商品详情回源加载器：查库并补齐图片，供 CacheUtil 在缓存未命中时回调。
     *
     * @param productId 商品 ID
     * @return 商品详情实体，不存在时返回 null
     */
    private Product loadProductDetail(Long productId) {
        Product product = productMapper.selectByIdWithDetails(productId);
        if (product != null) {
            fillSingleImages(product);
        }
        return product;
    }
```

Note: leave `PRODUCT_LOCK_KEY`, `RLock`, and `RedissonClient` imports/usages intact — `offShelf` and `onShelf` still use them. Only the `getDetail`/`loadAndCacheDetail` cache-rebuild code is removed.

- [ ] **Step 9: Run the product test class to verify it passes**

Run: `mvn -q -Dtest=ProductServiceImplTest test`
Expected: PASS — the two new `getDetail` tests plus the existing `publish*` tests.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/secondhand/service/impl/ProductServiceImpl.java \
        src/test/java/com/secondhand/service/impl/ProductServiceImplTest.java
git commit -m "refactor: route product detail caching through CacheUtil logical expire"
```

---

## Task 4: Full Regression

- [ ] **Step 1: Run the entire test suite**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`, all test classes green (including `UserControllerTest`, `EvaluationServiceImplTest`, `FavoriteServiceImplTest`, interceptor and config tests).

- [ ] **Step 2: Start the app and smoke-test (optional, requires MySQL + Redis running)**

Run: `mvn -q spring-boot:run`
Then in another shell: `BASE=http://localhost:8080 bash test-api.sh`
Expected: the existing API smoke script reports its PASS lines; no `500` HTML error pages.

- [ ] **Step 3: Commit any final adjustments**

```bash
git add -A
git commit -m "test: full regression for lock/cache/exception hardening"
```

---

## Self-Review

**1. Spec coverage**

- Redisson lock on `createOrder` → Task 2, Steps 3-4. ✓
- Lock on stock restore (cancel/reject) → Task 2, Step 5. ✓
- Lock-acquire-failure returns friendly error, no insert → Task 2, Step 1 test + Step 4 code. ✓
- Transaction-ordering simple variant (lock inside `@Transactional` method) → Task 2, Step 4 keeps `@Transactional` on `createOrder`. ✓ (the accepted window is documented in the spec)
- Cache penetration (null placeholder) → `queryWithPassThrough`, Task 3 Step 3. ✓
- Cache breakdown (mutex) → `queryWithMutex`, Task 3 Step 3. ✓
- Cache breakdown (logical expire) → `queryWithLogicalExpire`, Task 3 Step 3. ✓
- Cache avalanche (TTL jitter) → reused existing `set(...)` jitter, called by `queryWithPassThrough`. ✓
- `getDetail` collapses to one utility call + view count → Task 3 Step 8. ✓
- Global exception handler returns unified `Result` → Task 1. ✓
- Friendly handlers for common request errors → Task 1, Step 3. ✓

**2. Placeholder scan:** No `TBD`/`TODO`/"add error handling"/"write tests for the above". Every code and test step contains full content. ✓

**3. Type consistency:**
- Lock constant `ORDER_LOCK_KEY` and helpers `doCreateOrder`/`restoreProductStock` are consistent across Task 2. ✓
- `queryWithPassThrough(String, ID, Class<T>, Function<ID,T>, long, long)`, `queryWithMutex(String, ID, Class<T>, Function<ID,T>, long)`, `queryWithLogicalExpire(String, ID, Class<T>, Function<ID,T>, long, long)` — the signatures used in the `CacheUtil` tests (Task 3 Step 1), the implementations (Step 3), and the `getDetail` call site (Step 8) all match. ✓
- `loadProductDetail` is referenced as `this::loadProductDetail` (a `Function<Long,Product>`) and defined in the same step. ✓
- `NULL_PLACEHOLDER` is `public static` on `CacheUtil` (verified in current source) and referenced in the test. ✓

## Out of Scope (from the spec's Non-Goals)

Bean Validation / `@Valid`; externalizing DB credentials; auth role system / 401-vs-500; `order_no` generator overhaul; `BizException` refactor; atomic-UPDATE or optimistic locking for stock; frontend changes.
