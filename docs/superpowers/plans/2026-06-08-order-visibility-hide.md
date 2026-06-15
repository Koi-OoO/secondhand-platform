# Order Visibility Hide Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add buyer-side and seller-side completed-order hide actions that only affect each actor's own order list while preserving database records and aligned Swagger/frontend behavior.

**Architecture:** Extend the `order` table with two visibility flags and add actor-specific hide endpoints. Filter bought and sold order queries by the corresponding visibility flag, document the semantics in OpenAPI, and add frontend actions that call the correct hide endpoint for the active tab.

**Tech Stack:** Spring Boot 2.7, MyBatis-Plus, MySQL, Redis, SpringDoc OpenAPI, JUnit 5, Vue 3, Element Plus, Axios

---

## File Structure

**Backend schema and entities**
- Modify: `D:\my-project\secondhand-platform\src\main\resources\sql\init.sql`
- Create: `D:\my-project\secondhand-platform\src\main\resources\sql\2026-06-08-add-order-visibility-flags.sql`
- Modify: `D:\my-project\secondhand-platform\src\main\java\com\secondhand\entity\Order.java`

**Backend service and controller**
- Modify: `D:\my-project\secondhand-platform\src\main\java\com\secondhand\service\OrderService.java`
- Modify: `D:\my-project\secondhand-platform\src\main\java\com\secondhand\service\impl\OrderServiceImpl.java`
- Modify: `D:\my-project\secondhand-platform\src\main\java\com\secondhand\controller\OrderController.java`

**Backend tests and docs verification**
- Modify: `D:\my-project\secondhand-platform\src\test\java\com\secondhand\service\impl\OrderServiceImplTest.java`
- Modify: `D:\my-project\secondhand-platform\src\test\java\com\secondhand\controller\ApiDocsJsonTest.java`

**Frontend API and pages**
- Modify: `D:\my-project\secondhand-platform-web\src\api\order.js`
- Modify: `D:\my-project\secondhand-platform-web\src\views\user\MyOrdersPage.vue`

---

### Task 1: Add Failing Backend Tests for Actor-Scoped Hide

**Files:**
- Modify: `D:\my-project\secondhand-platform\src\test\java\com\secondhand\service\impl\OrderServiceImplTest.java`
- Modify: `D:\my-project\secondhand-platform\src\test\java\com\secondhand\controller\ApiDocsJsonTest.java`

- [ ] **Step 1: Read the current order and API docs test patterns**

Run:

```bash
rg -n "confirmOrder|cancelOrder|rejectOrder|sold|bought|api-docs|order" D:\my-project\secondhand-platform\src\test\java
```

Expected: locate the existing service and API-doc tests to follow project style.

- [ ] **Step 2: Add a failing buyer-hide service test**

Add a test equivalent to:

```java
@Test
void hideBoughtOrder_shouldMarkBuyerDeletedWhenBuyerOwnsCompletedOrder() {
    Order order = new Order();
    order.setId(10L);
    order.setBuyerId(6L);
    order.setSellerId(7L);
    order.setStatus(3);
    order.setBuyerDeleted(0);

    when(orderMapper.selectById(10L)).thenReturn(order);

    Result result = orderService.hideBoughtOrder(6L, 10L);

    assertEquals(200, result.getCode());
    assertEquals(1, order.getBuyerDeleted());
    verify(orderMapper).updateById(order);
}
```

- [ ] **Step 3: Add failing seller-hide and invalid-state tests**

Add tests that assert:

```java
@Test
void hideSoldOrder_shouldMarkSellerDeletedWhenSellerOwnsCompletedOrder() { ... }

@Test
void hideBoughtOrder_shouldRejectNonCompletedOrder() { ... }

@Test
void hideSoldOrder_shouldRejectWrongSeller() { ... }
```

- [ ] **Step 4: Add failing list-filter tests**

Add tests that assert:

```java
@Test
void getMyBoughtOrders_shouldFilterBuyerDeletedRecords() { ... }

@Test
void getMySoldOrders_shouldFilterSellerDeletedRecords() { ... }
```

Use the wrapper assertions or mapper interaction checks already used in this test file.

- [ ] **Step 5: Add a failing OpenAPI docs test**

Assert the generated docs include:

```java
assertThat(openApiJson).contains("/order/{id}/buyer");
assertThat(openApiJson).contains("/order/{id}/seller");
assertThat(openApiJson).contains("仅隐藏当前买家视角");
assertThat(openApiJson).contains("仅隐藏当前卖家视角");
```

- [ ] **Step 6: Run the targeted tests and verify they fail**

Run:

```bash
mvn -Dtest=OrderServiceImplTest,ApiDocsJsonTest test
```

Expected: FAIL because hide methods, fields, filters, and docs do not exist yet.

---

### Task 2: Implement Schema and Order Model Changes

**Files:**
- Modify: `D:\my-project\secondhand-platform\src\main\resources\sql\init.sql`
- Create: `D:\my-project\secondhand-platform\src\main\resources\sql\2026-06-08-add-order-visibility-flags.sql`
- Modify: `D:\my-project\secondhand-platform\src\main\java\com\secondhand\entity\Order.java`

- [ ] **Step 1: Add visibility columns to schema**

Add to `order` table in `init.sql`:

```sql
`buyer_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '买家视角是否隐藏：0否 1是',
`seller_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '卖家视角是否隐藏：0否 1是',
```

- [ ] **Step 2: Create the migration script**

Create `2026-06-08-add-order-visibility-flags.sql` with:

```sql
ALTER TABLE `order`
  ADD COLUMN `buyer_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '买家视角是否隐藏：0否 1是' AFTER `status`,
  ADD COLUMN `seller_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '卖家视角是否隐藏：0否 1是' AFTER `buyer_deleted`;
```

- [ ] **Step 3: Extend the order entity**

Add to `Order.java`:

```java
@Schema(description = "买家视角是否隐藏：0否 1是", example = "0")
private Integer buyerDeleted;

@Schema(description = "卖家视角是否隐藏：0否 1是", example = "0")
private Integer sellerDeleted;
```

- [ ] **Step 4: Run compile to surface type mismatches**

Run:

```bash
mvn -DskipTests compile
```

Expected: compile may still fail until service/controller signatures are updated, but entity-level issues should be visible.

---

### Task 3: Implement Backend Hide Actions and Query Filtering

**Files:**
- Modify: `D:\my-project\secondhand-platform\src\main\java\com\secondhand\service\OrderService.java`
- Modify: `D:\my-project\secondhand-platform\src\main\java\com\secondhand\service\impl\OrderServiceImpl.java`

- [ ] **Step 1: Add service contract methods**

Add to `OrderService.java`:

```java
Result hideBoughtOrder(Long buyerId, Long orderId);
Result hideSoldOrder(Long sellerId, Long orderId);
```

- [ ] **Step 2: Filter bought and sold lists by visibility**

Update the query wrappers to include:

```java
wrapper.eq(Order::getBuyerDeleted, 0);
```

for bought orders, and:

```java
wrapper.eq(Order::getSellerDeleted, 0);
```

for sold orders.

- [ ] **Step 3: Implement buyer hide logic**

Implement:

```java
@Transactional
public Result hideBoughtOrder(Long buyerId, Long orderId) {
    Order order = orderMapper.selectById(orderId);
    if (order == null) return Result.error("订单不存在");
    if (!order.getBuyerId().equals(buyerId)) return Result.error("无权操作");
    if (order.getStatus() != 3) return Result.error("仅已完成订单可删除记录");
    if (Integer.valueOf(1).equals(order.getBuyerDeleted())) return Result.error("订单记录已删除");
    order.setBuyerDeleted(1);
    orderMapper.updateById(order);
    return Result.success("已从买家订单记录中删除");
}
```

- [ ] **Step 4: Implement seller hide logic**

Implement:

```java
@Transactional
public Result hideSoldOrder(Long sellerId, Long orderId) {
    Order order = orderMapper.selectById(orderId);
    if (order == null) return Result.error("订单不存在");
    if (!order.getSellerId().equals(sellerId)) return Result.error("无权操作");
    if (order.getStatus() != 3) return Result.error("仅已完成订单可删除记录");
    if (Integer.valueOf(1).equals(order.getSellerDeleted())) return Result.error("订单记录已删除");
    order.setSellerDeleted(1);
    orderMapper.updateById(order);
    return Result.success("已从卖家订单记录中删除");
}
```

- [ ] **Step 5: Run order service tests**

Run:

```bash
mvn -Dtest=OrderServiceImplTest test
```

Expected: the new service behavior and list filtering tests pass.

---

### Task 4: Add Controller Endpoints and Swagger Documentation

**Files:**
- Modify: `D:\my-project\secondhand-platform\src\main\java\com\secondhand\controller\OrderController.java`
- Modify: `D:\my-project\secondhand-platform\src\test\java\com\secondhand\controller\ApiDocsJsonTest.java`

- [ ] **Step 1: Add buyer hide endpoint**

Add:

```java
@DeleteMapping("/{id}/buyer")
@Operation(summary = "买家删除订单记录", description = "需要登录，仅允许买家将自己的已完成订单从买家视角隐藏，数据库记录保留，卖家仍可见。")
public Result hideBought(@PathVariable Long id, HttpSession session) { ... }
```

- [ ] **Step 2: Add seller hide endpoint**

Add:

```java
@DeleteMapping("/{id}/seller")
@Operation(summary = "卖家删除订单记录", description = "需要登录，仅允许卖家将自己的已完成订单从卖家视角隐藏，数据库记录保留，买家仍可见。")
public Result hideSold(@PathVariable Long id, HttpSession session) { ... }
```

- [ ] **Step 3: Clarify existing list endpoint docs**

Update descriptions for:
- `/order/bought`
- `/order/sold`

to mention that records hidden in the current actor's own view are excluded.

- [ ] **Step 4: Run the docs test**

Run:

```bash
mvn -Dtest=ApiDocsJsonTest test
```

Expected: PASS with new paths and descriptions present.

---

### Task 5: Verify Backend End-to-End and Online API Docs

**Files:**
- Reuse backend files above

- [ ] **Step 1: Run the focused backend test set**

Run:

```bash
mvn -Dtest=OrderServiceImplTest,ApiDocsJsonTest test
```

Expected: PASS.

- [ ] **Step 2: Run the full backend test suite**

Run:

```bash
mvn test
```

Expected: PASS.

- [ ] **Step 3: Start the backend and verify online docs**

Run:

```bash
mvn spring-boot:run
```

Then check:

```bash
Invoke-RestMethod -Uri http://localhost:8080/v3/api-docs
```

Expected: JSON contains `/order/{id}/buyer` and `/order/{id}/seller`.

---

### Task 6: Align Frontend API Client and Order Page

**Files:**
- Modify: `D:\my-project\secondhand-platform-web\src\api\order.js`
- Modify: `D:\my-project\secondhand-platform-web\src\views\user\MyOrdersPage.vue`

- [ ] **Step 1: Add actor-specific hide API methods**

Add to `src/api/order.js`:

```js
export function hideBoughtOrder(id) {
  return http.delete(`/order/${id}/buyer`)
}

export function hideSoldOrder(id) {
  return http.delete(`/order/${id}/seller`)
}
```

- [ ] **Step 2: Show hide action only for completed orders**

In `MyOrdersPage.vue`:
- buyer tab: show `删除记录` when `order.status === 3`
- seller tab: show `删除记录` when `order.status === 3`

Do not show it for other statuses.

- [ ] **Step 3: Implement confirmation and in-list removal**

Add handlers equivalent to:

```js
async function handleHide(order) {
  const api = tab.value === 'bought' ? hideBoughtOrder : hideSoldOrder
  await api(order.id)
  list.value = list.value.filter(item => item.id !== order.id)
}
```

Use the existing confirmation and loading style already present in the page.

- [ ] **Step 4: Run frontend build**

Run:

```bash
npm run build
```

Workdir:

```bash
D:\my-project\secondhand-platform-web
```

Expected: PASS.

---

### Task 7: Run Frontend and Full Integration Verification

**Files:**
- Reuse backend and frontend files above

- [ ] **Step 1: Verify seller-side hide flow**

With backend and frontend running:
- complete an order
- open seller tab
- delete the record
- confirm it disappears from seller view

- [ ] **Step 2: Verify buyer-side persistence after seller hide**

Check the same order in buyer tab:
- it must still be present before buyer deletion

- [ ] **Step 3: Verify buyer-side hide flow**

Delete the same completed order from buyer tab:
- confirm it disappears from buyer view

- [ ] **Step 4: Final verification commands**

Backend:

```bash
mvn test
```

Frontend:

```bash
npm run build
```

API docs:

```bash
Invoke-RestMethod -Uri http://localhost:8080/v3/api-docs
```

Expected: all succeed and docs include the two hide endpoints.

---

## Self-Review

### Spec Coverage

- buyer-side hide: Tasks 1, 3, 4, 6, 7
- seller-side hide: Tasks 1, 3, 4, 6, 7
- DB preservation: Task 3
- bought/sold filtering: Task 3
- Swagger alignment: Tasks 1, 4, 5
- frontend alignment: Task 6
- end-to-end validation: Task 7

No known spec gaps remain.

### Placeholder Scan

- No `TODO`, `TBD`, or deferred implementation placeholders remain.
- Every task includes exact files and verification commands.

### Type Consistency

- `buyerDeleted` and `sellerDeleted` are consistently treated as order-level integer flags
- `hideBoughtOrder(Long buyerId, Long orderId)` and `hideSoldOrder(Long sellerId, Long orderId)` are used consistently across plan tasks
