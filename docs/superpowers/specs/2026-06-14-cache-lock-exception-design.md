# Order Lock, Cache Utility, and Global Exception Handling Design

## Goal

Strengthen three high-value, interview-relevant robustness aspects of the existing backend without adding new product features:

1. Prevent stock oversell on concurrent order creation using a Redisson distributed lock.
2. Consolidate the three classic cache problems (avalanche, penetration, breakdown) into one reusable, generic cache utility.
3. Add a global exception handler so every uncaught error returns the unified `Result` JSON.

This is a portfolio project intended to support a Java backend internship search, so the design favors well-understood, clearly explainable patterns over heavier production architecture. Where a stricter variant exists, it is noted as an interview discussion point rather than adopted by default.

## Current State

### Order / stock concurrency
- `OrderServiceImpl.createOrder` (`OrderServiceImpl.java:80-139`) reads the product, checks stock, then does `product.setStock(stock - quantity)` + `updateById`. It is `@Transactional` but has no lock, no atomic update, and no version column. Two concurrent buyers of the last unit both pass the check and both deduct → oversell.
- The duplicate-order guard (`OrderServiceImpl.java:102-109`) is a `selectCount` followed by `insert` — a check-then-act race.
- `restoreProductStock` (`OrderServiceImpl.java:697-713`, used by cancel and reject) has the same read-modify-write race on stock.
- `offShelf` / `onShelf` already use a Redisson lock keyed `product:lock:{id}`. The order/stock path does not — the most concurrency-sensitive path is the least protected.

### Cache
- `CacheUtil` already implements most building blocks: random TTL jitter (`set`), null placeholder for penetration (`setNull` / `isNullPlaceholder`), logical expiration for breakdown (`setWithLogicalExpire` / `getWithLogicalExpire` / `getEvenIfExpired`), and a generic pass-through template (`getOrLoad`).
- The mutex-lock cache rebuild for breakdown lives inside `ProductServiceImpl.getDetail` (`ProductServiceImpl.java:160-253`), not in `CacheUtil`. The utility is therefore not self-contained: the "breakdown via mutex lock" strategy cannot be reused without copying business code.

### Exceptions
- No `@RestControllerAdvice` exists. Uncaught exceptions return Spring's default error response (HTML/JSON error page), not the unified `Result` structure. Examples that can leak today: `order_no` unique-key collision, DB errors, NPEs.

### Available infrastructure
- `RedissonClient` is configured (`RedissonConfig`, single-server mode) and ready to inject.
- `Result` exposes `code = 200` (success) and `code = 500` (error) via `Result.success(data)` / `Result.error(message)`.

## Approaches Considered (stock concurrency mechanism)

### Approach A: Atomic conditional UPDATE
`UPDATE product SET stock = stock - #{qty} ... WHERE id = #{id} AND stock >= #{qty}`, check affected rows.

Pros:
- simplest, highest throughput, no extra infrastructure
- matches the existing `incrementViewCount` / `incrementLikeCount` idiom

Cons:
- does not showcase distributed-lock skills, which are an explicit goal of this project
- a second mechanism would still be needed for the duplicate-order rule

Rejected for this project (kept as an interview comparison point).

### Approach B: Redisson distributed lock
Acquire a per-product lock around the order-creation critical section.

Pros:
- consistent with the existing `offShelf` / `onShelf` lock model — one mental model for all stock mutations
- a single lock also serializes the duplicate-order guard
- demonstrates Redisson distributed locking, a common interview topic

Cons:
- serializes all buyers of the same product (acceptable: second-hand listings are typically single-unit)
- lock and transaction commit ordering must be understood (see Part 1)

Chosen.

### Approach C: MyBatis-Plus `@Version` optimistic lock
Add a `version` column and the optimistic-locker interceptor; retry on conflict.

Pros:
- idiomatic MyBatis-Plus; protects all concurrent product updates

Cons:
- requires a schema change and global config affecting every product update
- needs a retry loop; overkill for a single counter decrement

Rejected.

## Part 1: Distributed Lock for Order Stock

### Lock placement and key
- Inject `RedissonClient` into `OrderServiceImpl` (it currently has only `RedisTemplate`).
- Lock key: `order:lock:product:{productId}`. Per-product, so different products do not block each other.
- Acquisition: `tryLock(3, 10, TimeUnit.SECONDS)` (wait 3s, lease 10s), mirroring `offShelf` / `onShelf`.
- On acquisition failure: return `Result.error("下单的人有点多，请稍后再试")`.
- Release in `finally`, guarded by `lock.isHeldByCurrentThread()`.
- Handle `InterruptedException`: restore the interrupt flag and return a "系统繁忙，请重试" error.

### Critical section (unchanged business logic, now serialized)
Inside the lock, keep the existing flow: load product, validate (on-sale status, not self-purchase, sufficient stock, no existing unfinished order for this buyer+product), generate order number, insert order, deduct stock and flip product status when stock reaches zero, evict the `product:detail:{id}` cache.

### Stock restore paths
`cancelOrder` and `rejectOrder` restore stock through `restoreProductStock`. Wrap that restore in the same `order:lock:product:{productId}` lock so all stock mutations for a product are serialized under one key. This removes the restore read-modify-write race.

### Transaction ordering (interview discussion point)
Because `createOrder` is `@Transactional`, a lock held *inside* the method is released when the method body finishes — which is *before* the Spring proxy commits the transaction. That leaves a small window where another thread can acquire the lock and read pre-commit data.

- Default for this project (simple): keep the lock inside the service method. The window is acknowledged as a known limitation and is a useful thing to be able to explain in an interview.
- Optional stricter variant (no new bean): acquire and release the lock in `OrderController.create`, wrapping the call to the proxied `@Transactional` service method, so the lock spans the full transaction including commit.

This spec adopts the simple variant.

## Part 2: Cache Utility

Refactor `CacheUtil` into a self-contained, generic component that exposes the three classic cache strategies. Each method takes a `Function<ID, T>` database fallback (functional interface), so business code passes only a loader.

### Methods
- `queryWithPassThrough(keyPrefix, id, type, dbFallback, ttlSeconds, jitterSeconds)`
  - Prevents **penetration** via the null placeholder; prevents **avalanche** via random TTL jitter.
  - Generalizes the existing `getOrLoad`.
- `queryWithMutex(keyPrefix, id, type, dbFallback, ttlSeconds)`
  - Prevents **breakdown** via a Redisson mutex, cold-start friendly: on cache miss, one thread acquires the lock and rebuilds from the loader; other threads sleep briefly and retry the read instead of all hitting the database.
  - A second, simpler breakdown strategy provided for reuse and as an interview comparison point — not the strategy `getDetail` uses.
- `queryWithLogicalExpire(keyPrefix, id, type, dbFallback, logicalTtlSeconds)`
  - Prevents **breakdown** for hot keys: on logical expiration, a single thread rebuilds under a lock while all callers immediately get the stale value (no blocking).
  - This consolidates the logic currently inlined in `ProductServiceImpl.getDetail` (logical-expire read via `getWithLogicalExpire`, lock-guarded single-flight rebuild, stale fallback via `getEvenIfExpired` on lock contention).

### Supporting changes
- Inject `RedissonClient` into `CacheUtil` (needed by `queryWithMutex`).
- Keep `set(...)` random TTL jitter as the avalanche defense.
- `ProductServiceImpl.getDetail` collapses to: call one cache-utility method (logical expire, preserving current behavior), then `incrementViewCount`, then return. The "product not found" placeholder result and stale fallback semantics are preserved by the utility.

### Reusability note
The utility must not reference `Product` or any specific entity. It works for any cacheable entity keyed by id, which is what makes it a presentable, reusable component.

## Part 3: Global Exception Handler

Add `GlobalExceptionHandler` annotated `@RestControllerAdvice`.

### Handlers
- `@ExceptionHandler(Exception.class)`: log the full exception server-side, return `Result.error("服务器繁忙，请稍后再试")`. No stack trace or internal detail is sent to the client.
- Friendly handlers for common request errors, each returning a clear `Result.error(...)`:
  - `MethodArgumentTypeMismatchException` (bad path/query param type)
  - `MissingServletRequestParameterException` (missing required param)
  - `HttpMessageNotReadableException` (unparseable request body)

### Result
Every uncaught exception returns the unified `{ "code": 500, "message": ..., "data": null }` JSON, matching the existing contract, instead of Spring's default error page.

### Optional enhancement (not in scope)
A `BizException extends RuntimeException` would let services `throw` business errors instead of returning `Result.error(...)` through every layer. It is cleaner but touches every service method, so it is deferred.

## Testing Requirements

Tests follow the existing pure-Mockito style (mock mappers, `ReflectionTestUtils` field injection, behavior verified via `verify` / `ArgumentCaptor`).

### Order lock
- Add `RedissonClient` and `RLock` mocks to `OrderServiceImplTest`.
- The two existing tests that assert `verify(productMapper).updateById(...)` and in-memory stock mutation (`createOrderPersistsQuantityAndDeductsStock`, `createOrderRejectsWhenRequestedQuantityExceedsAvailableStock`) are updated for the lock-based flow.
- New: when `tryLock` returns `false`, `createOrder` returns an error and performs no insert or stock change.
- New: cancel and reject still restore stock correctly under the lock.

### Cache utility
- New unit tests for `CacheUtil` mocking `RedisTemplate` and `RedissonClient`:
  - cache hit returns the cached value without calling the loader
  - cache miss calls the loader and writes the value
  - null from loader writes the null placeholder and returns null
  - `queryWithMutex` retries the read instead of rebuilding when the lock is held by another thread
  - `queryWithLogicalExpire` returns the stale value (and does not block) when logically expired

### Global exception handler
- A `MockMvc` test (or a controller-advice unit test) triggers an exception and asserts the response is the unified `Result` structure with `code = 500`.

### Regression
- All existing backend tests continue to pass.

## Non-Goals

This change does not include:
- Bean Validation / `@Valid` migration
- externalizing DB credentials or other config secrets
- an auth role system or 401-vs-500 status normalization
- reworking the `order_no` generator (collision is rare; deferred)
- a `BizException` refactor across services
- atomic conditional UPDATE or optimistic locking for stock (Redisson lock chosen instead)
- frontend changes
