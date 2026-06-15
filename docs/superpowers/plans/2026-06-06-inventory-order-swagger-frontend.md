# Inventory, Order Quantity, Swagger, and Frontend Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add product stock and order quantity support, update Swagger/OpenAPI, verify the new backend behavior through tests and API checks, then align the frontend publish/detail/order pages with the new contract.

**Architecture:** Keep the existing product/order model and extend it with `product.stock` and `order.quantity`. Reuse the current `status` field semantics, Redisson locking style, and Result-based API shape. Drive the change from backend model and service logic first, then update generated API docs and finally align frontend request/response handling.

**Tech Stack:** Spring Boot 2.7, MyBatis-Plus, Redis, Redisson, SpringDoc OpenAPI, JUnit 5, Vue 3, Element Plus, Axios

---

## File Structure

**Backend schema and mappings**
- Modify: `D:\my-project\secondhand-platform\src\main\resources\sql\init.sql`
- Create: `D:\my-project\secondhand-platform\src\main\resources\sql\2026-06-06-add-stock-and-order-quantity.sql`
- Modify: `D:\my-project\secondhand-platform\src\main\resources\mapper\ProductMapper.xml`

**Backend entities and interfaces**
- Modify: `D:\my-project\secondhand-platform\src\main\java\com\secondhand\entity\Product.java`
- Modify: `D:\my-project\secondhand-platform\src\main\java\com\secondhand\entity\Order.java`
- Modify: `D:\my-project\secondhand-platform\src\main\java\com\secondhand\service\OrderService.java`
- Modify: `D:\my-project\secondhand-platform\src\main\java\com\secondhand\controller\OrderController.java`
- Modify: `D:\my-project\secondhand-platform\src\main\java\com\secondhand\controller\ProductController.java`

**Backend service logic**
- Modify: `D:\my-project\secondhand-platform\src\main\java\com\secondhand\service\impl\ProductServiceImpl.java`
- Modify: `D:\my-project\secondhand-platform\src\main\java\com\secondhand\service\impl\OrderServiceImpl.java`

**Backend tests**
- Modify: `D:\my-project\secondhand-platform\src\test\java\com\secondhand\service\impl\ProductServiceImplTest.java`
- Modify: `D:\my-project\secondhand-platform\src\test\java\com\secondhand\service\impl\OrderServiceImplTest.java`
- Modify: `D:\my-project\secondhand-platform\src\test\java\com\secondhand\controller\ProductControllerOpenApiTest.java`
- Modify: `D:\my-project\secondhand-platform\test-api.sh`

**Frontend API and pages**
- Modify: `D:\my-project\secondhand-platform-web\src\api\order.js`
- Modify: `D:\my-project\secondhand-platform-web\src\api\product.js`
- Modify: `D:\my-project\secondhand-platform-web\src\views\product\ProductPublishPage.vue`
- Modify: `D:\my-project\secondhand-platform-web\src\views\product\ProductDetailPage.vue`
- Modify: `D:\my-project\secondhand-platform-web\src\views\user\MyOrdersPage.vue`

---

### Task 1: Add Failing Backend Tests for Stock and Quantity

**Files:**
- Modify: `D:\my-project\secondhand-platform\src\test\java\com\secondhand\service\impl\ProductServiceImplTest.java`
- Modify: `D:\my-project\secondhand-platform\src\test\java\com\secondhand\service\impl\OrderServiceImplTest.java`
- Modify: `D:\my-project\secondhand-platform\src\test\java\com\secondhand\controller\ProductControllerOpenApiTest.java`

- [ ] **Step 1: Read the current test shape before adding coverage**

Run:

```bash
rg -n "publish|createOrder|cancelOrder|rejectOrder|openapi|stock|quantity" D:\my-project\secondhand-platform\src\test\java
```

Expected: existing product/order/openapi tests are located so the new assertions can follow local patterns.

- [ ] **Step 2: Add a failing product publish/update stock contract test**

Add tests that assert:

```java
@Test
void publish_shouldRejectMissingOrNonPositiveStock() {
    Product product = new Product();
    product.setTitle("Test Product");
    product.setPrice(new BigDecimal("10.00"));
    product.setConditionLevel(1);

    Result result = productService.publish(1L, product, List.of("/uploads/a.png"));

    assertEquals(500, result.getCode());
}
```

and

```java
@Test
void publish_shouldPersistStockWhenValid() {
    Product product = new Product();
    product.setTitle("Keyboard");
    product.setPrice(new BigDecimal("99.00"));
    product.setConditionLevel(2);
    product.setStock(5);

    when(categoryMapper.selectById(any())).thenReturn(null);
}
```

Use the actual mocking style already present in the file, but force assertions on `product.getStock()` persistence and validation behavior.

- [ ] **Step 3: Add failing order quantity and stock mutation tests**

Add tests that assert:

```java
@Test
void createOrder_shouldRejectWhenRequestedQuantityExceedsStock() { ... }

@Test
void createOrder_shouldDeductStockAndStoreQuantity() { ... }

@Test
void cancelOrder_shouldRestoreStockByOrderQuantity() { ... }

@Test
void rejectOrder_shouldRestoreStockByOrderQuantity() { ... }
```

Minimum assertions:
- `order.quantity` is stored
- `product.stock` is reduced by quantity
- `product.status` becomes `3` when stock becomes zero
- cancel/reject restore `stock`

- [ ] **Step 4: Add a failing OpenAPI test for the new fields**

Add assertions in the OpenAPI test for:

```java
assertThat(openApiJson).contains("\"stock\"");
assertThat(openApiJson).contains("\"quantity\"");
assertThat(openApiJson).contains("/order/create");
```

Also assert the create-order parameter list includes `quantity`.

- [ ] **Step 5: Run the targeted tests and verify they fail for the right reason**

Run:

```bash
mvn -Dtest=ProductServiceImplTest,OrderServiceImplTest,ProductControllerOpenApiTest test
```

Expected: FAIL because `stock`/`quantity` fields and logic are not implemented yet.

---

### Task 2: Implement Schema, Entity, and API Contract Changes

**Files:**
- Modify: `D:\my-project\secondhand-platform\src\main\resources\sql\init.sql`
- Create: `D:\my-project\secondhand-platform\src\main\resources\sql\2026-06-06-add-stock-and-order-quantity.sql`
- Modify: `D:\my-project\secondhand-platform\src\main\java\com\secondhand\entity\Product.java`
- Modify: `D:\my-project\secondhand-platform\src\main\java\com\secondhand\entity\Order.java`
- Modify: `D:\my-project\secondhand-platform\src\main\java\com\secondhand\service\OrderService.java`
- Modify: `D:\my-project\secondhand-platform\src\main\java\com\secondhand\controller\OrderController.java`

- [ ] **Step 1: Update the schema files**

Add to `product`:

```sql
`stock` INT NOT NULL DEFAULT 1 COMMENT '当前可售库存',
```

Add to `order`:

```sql
`quantity` INT NOT NULL DEFAULT 1 COMMENT '购买数量',
```

Create migration file with:

```sql
ALTER TABLE `product`
  ADD COLUMN `stock` INT NOT NULL DEFAULT 1 COMMENT '当前可售库存' AFTER `status`;

ALTER TABLE `order`
  ADD COLUMN `quantity` INT NOT NULL DEFAULT 1 COMMENT '购买数量' AFTER `product_id`;
```

- [ ] **Step 2: Extend the entity models**

Add to `Product.java`:

```java
@Schema(description = "当前可售库存", example = "5")
private Integer stock;
```

Add to `Order.java`:

```java
@Schema(description = "购买数量", example = "2")
private Integer quantity;
```

- [ ] **Step 3: Change the order service and controller contract**

Update `OrderService.java`:

```java
Result createOrder(Long buyerId, Long productId, Integer quantity, String address);
```

Update `OrderController.java` create endpoint:

```java
public Result create(@RequestParam Long productId,
                     @RequestParam Integer quantity,
                     @RequestParam String address,
                     HttpSession session)
```

Keep the controller style consistent with the existing `SessionUtil.getUserId(session)` flow.

- [ ] **Step 4: Run compile to catch signature mismatches early**

Run:

```bash
mvn -DskipTests compile
```

Expected: compile may still fail until service implementations are updated, but all contract-level type mismatches should be visible.

---

### Task 3: Implement Product Stock Validation and Cache-Safe Persistence

**Files:**
- Modify: `D:\my-project\secondhand-platform\src\main\java\com\secondhand\service\impl\ProductServiceImpl.java`

- [ ] **Step 1: Add stock validation to publish**

In the publish path, add checks equivalent to:

```java
if (product.getStock() == null || product.getStock() < 1) {
    return Result.error("库存必须大于 0");
}
```

Ensure publish persists `stock` directly on insert.

- [ ] **Step 2: Add stock validation to update**

In the update validation path, add:

```java
if (product.getStock() != null && product.getStock() < 1) {
    return Result.error("库存必须大于 0");
}
```

Do not auto-convert stock `0` into a valid edit path for this iteration.

- [ ] **Step 3: Make stock part of cached detail behavior**

Ensure no special mapping excludes the new field, and keep existing cache invalidation after:
- publish
- update
- off-shelf
- on-shelf

No new abstraction is needed; just preserve the existing cache invalidation pattern.

- [ ] **Step 4: Run the product-focused tests**

Run:

```bash
mvn -Dtest=ProductServiceImplTest test
```

Expected: publish/update stock tests now pass; order tests can still fail.

---

### Task 4: Implement Quantity-Based Ordering and Stock Restoration

**Files:**
- Modify: `D:\my-project\secondhand-platform\src\main\java\com\secondhand\service\impl\OrderServiceImpl.java`

- [ ] **Step 1: Make createOrder fail fast on invalid quantity**

Add checks:

```java
if (quantity == null || quantity < 1) {
    return Result.error("购买数量必须大于 0");
}
```

Keep address validation as-is.

- [ ] **Step 2: Protect stock deduction with product-level locking**

Reuse the existing Redisson product lock pattern conceptually:

```java
String lockKey = "product:lock:" + productId;
```

Acquire lock before checking stock and creating the order.

- [ ] **Step 3: Deduct stock and persist order quantity**

Implement the core mutation:

```java
order.setQuantity(quantity);
order.setProductAmount(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
order.setTotalAmount(order.getProductAmount());

product.setStock(product.getStock() - quantity);
product.setStatus(product.getStock() == 0 ? 3 : 1);
```

Keep all of this inside the same transaction that inserts the order.

- [ ] **Step 4: Restore stock on cancel**

When cancel succeeds:

```java
product.setStock(product.getStock() + order.getQuantity());
if (product.getStock() > 0) {
    product.setStatus(1);
}
```

Continue clearing `product:detail:<id>` cache afterwards.

- [ ] **Step 5: Restore stock on reject**

Use the same restoration logic as cancel.

- [ ] **Step 6: Keep order list enrichment compatible with quantity**

No new lookup is needed, but ensure the returned `Order` records expose the `quantity` field naturally to the frontend.

- [ ] **Step 7: Run the order-focused tests**

Run:

```bash
mvn -Dtest=OrderServiceImplTest test
```

Expected: quantity persistence and stock restore tests pass.

---

### Task 5: Update Swagger/OpenAPI and Backend API Smoke Checks

**Files:**
- Modify: `D:\my-project\secondhand-platform\src\main\java\com\secondhand\entity\Product.java`
- Modify: `D:\my-project\secondhand-platform\src\main\java\com\secondhand\entity\Order.java`
- Modify: `D:\my-project\secondhand-platform\src\main\java\com\secondhand\controller\OrderController.java`
- Modify: `D:\my-project\secondhand-platform\src\main\java\com\secondhand\controller\ProductController.java`
- Modify: `D:\my-project\secondhand-platform\src\test\java\com\secondhand\controller\ProductControllerOpenApiTest.java`
- Modify: `D:\my-project\secondhand-platform\test-api.sh`

- [ ] **Step 1: Update OpenAPI annotations for stock and quantity**

Add field docs such as:

```java
@Schema(description = "当前可售库存", example = "5")
private Integer stock;
```

and

```java
@Schema(description = "购买数量", example = "2")
private Integer quantity;
```

Also update create-order endpoint docs to mention `quantity`.

- [ ] **Step 2: Expand the shell smoke test**

Adjust the script so it:
- registers and logs in
- publishes a product with stock
- places an order with quantity
- validates stock changed
- cancels or rejects and validates stock restored
- checks `/v3/api-docs` contains `stock` and `quantity`

- [ ] **Step 3: Run the OpenAPI test**

Run:

```bash
mvn -Dtest=ProductControllerOpenApiTest test
```

Expected: PASS with OpenAPI assertions for the new fields.

- [ ] **Step 4: Run the backend compile-and-test set**

Run:

```bash
mvn test
```

Expected: all backend tests pass.

---

### Task 6: Align Frontend API Clients and Forms

**Files:**
- Modify: `D:\my-project\secondhand-platform-web\src\api\product.js`
- Modify: `D:\my-project\secondhand-platform-web\src\api\order.js`
- Modify: `D:\my-project\secondhand-platform-web\src\views\product\ProductPublishPage.vue`

- [ ] **Step 1: Update frontend API helpers**

Change order API helper to:

```js
export function createOrder(productId, quantity, address) {
  return http.post('/order/create', null, { params: { productId, quantity, address } })
}
```

Publish/update calls do not need new helper names; they just need to pass `stock` in `data`.

- [ ] **Step 2: Add stock to publish/edit form state**

In `ProductPublishPage.vue`, extend form model:

```js
stock: 1
```

Add validation:

```js
stock: [{ required: true, message: '请输入库存', trigger: 'blur' }]
```

and an `el-input-number` field for stock.

- [ ] **Step 3: Load and submit stock in edit/publish flows**

Ensure:
- edit mode reads `data.stock`
- publish request includes `stock`
- update request includes `stock`

- [ ] **Step 4: Run the frontend build**

Run:

```bash
npm run build
```

Workdir:

```bash
D:\my-project\secondhand-platform-web
```

Expected: frontend compiles with the new field bindings.

---

### Task 7: Align Product Detail and My Orders Pages

**Files:**
- Modify: `D:\my-project\secondhand-platform-web\src\views\product\ProductDetailPage.vue`
- Modify: `D:\my-project\secondhand-platform-web\src\views\user\MyOrdersPage.vue`

- [ ] **Step 1: Add stock display and quantity selector to detail page**

Add UI state like:

```js
const buyQuantity = ref(1)
```

Render:
- current stock text
- quantity stepper/input limited to `product.stock`

- [ ] **Step 2: Pass quantity when creating an order**

Update the purchase action to call:

```js
await createOrder(product.value.id, buyQuantity.value, address.trim())
```

After success:
- reduce local `product.stock`
- if stock becomes `0`, mark `status = 3`

- [ ] **Step 3: Show quantity and total meaning on the orders page**

Add display lines such as:

```vue
<div class="order-card__qty">数量: {{ o.quantity }}</div>
<div class="order-card__price">￥{{ formatPriceNum(o.totalAmount) }}</div>
```

Make the UI show both unit-subtotal context if needed, but do not redesign the page structure beyond what is needed for clarity.

- [ ] **Step 4: Run the frontend build again**

Run:

```bash
npm run build
```

Workdir:

```bash
D:\my-project\secondhand-platform-web
```

Expected: PASS after detail/order page updates.

---

### Task 8: End-to-End Verification

**Files:**
- Modify: `D:\my-project\secondhand-platform\test-api.sh`
- Reuse modified backend and frontend files above

- [ ] **Step 1: Start backend and frontend if not already running**

Backend:

```bash
mvn spring-boot:run
```

Frontend:

```bash
npm run dev
```

- [ ] **Step 2: Verify backend API contract manually**

Run:

```bash
bash test-api.sh
```

Expected:
- publish with stock succeeds
- detail returns stock
- create order with quantity succeeds
- over-quantity purchase fails
- cancel/reject restores stock
- api docs contain `stock` and `quantity`

- [ ] **Step 3: Verify frontend manually against the live backend**

Check:
- publish/edit page accepts stock
- detail page shows stock and quantity selector
- buy flow sends quantity correctly
- order page shows quantity

- [ ] **Step 4: Final full verification commands**

Backend:

```bash
mvn test
```

Frontend:

```bash
npm run build
```

Smoke:

```bash
bash test-api.sh
```

Expected: all three succeed.

---

## Self-Review

### Spec Coverage

- Stock field added: Task 2, Task 3, Task 6
- Quantity field added: Task 2, Task 4, Task 7
- Stock deduction/restoration: Task 4
- Swagger/OpenAPI update: Task 5
- API tests: Task 1, Task 5, Task 8
- Frontend alignment: Task 6, Task 7

No known spec gaps remain.

### Placeholder Scan

- No `TODO`, `TBD`, or “implement later” placeholders remain.
- All tasks reference exact file paths and exact verification commands.

### Type Consistency

- `product.stock` is always `Integer`
- `order.quantity` is always `Integer`
- `createOrder(Long buyerId, Long productId, Integer quantity, String address)` is used consistently across plan tasks

