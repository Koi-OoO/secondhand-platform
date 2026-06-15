# Order Visibility Hide Design

## Goal

Add order hide behavior for both buyer and seller views without physically deleting orders or breaking the other party's history.

This design must:
- let sellers hide only their own completed sold orders from the sold list
- let buyers hide only their own completed bought orders from the bought list
- keep the underlying order row and related evaluation data in the database
- expose the behavior clearly in Swagger / OpenAPI and keep frontend behavior aligned with the backend contract

## Current State

- The backend order flow supports create, bought list, sold list, cancel, ship, reject, confirm, and pending-count.
- There is no order delete or hide behavior.
- `GET /order/bought` returns all orders for the buyer.
- `GET /order/sold` returns all orders for the seller.
- The frontend order page supports buyer and seller actions for lifecycle changes, but not hiding completed history.
- Swagger annotations exist on `OrderController`, but the order contract is not fully explicit about list semantics and terminal-state actions.

## Business Rules

1. Hiding an order is a view-level action, not a physical delete.
2. Buyer hide and seller hide are independent.
3. Only completed orders can be hidden.
4. A buyer can hide only their own bought order.
5. A seller can hide only their own sold order.
6. Hidden orders disappear only from the current actor's list.
7. The other party must still see the same order in their own history.
8. Evaluation, payment snapshot, address, and audit-relevant order data remain intact.

## Approaches Considered

### Approach A: Physical delete from `order`

Pros:
- simplest API surface

Cons:
- destroys buyer history
- breaks evaluation linkage and audit value
- conflicts with the requested semantics

Rejected.

### Approach B: Single `seller_deleted` flag

Pros:
- minimal change for seller-side hide

Cons:
- does not support the newly expanded buyer-side hide requirement

Rejected.

### Approach C: Independent visibility flags on `order`

Add:
- `buyer_deleted`
- `seller_deleted`

Pros:
- directly models the two independent user views
- preserves database rows
- keeps list filtering logic simple
- leaves room for future “restore visibility” behavior

Cons:
- requires schema change and query updates

Chosen.

## Data Design

Add to `order`:

- `buyer_deleted TINYINT NOT NULL DEFAULT 0`
- `seller_deleted TINYINT NOT NULL DEFAULT 0`

Semantics:
- `0`: visible in that actor's list
- `1`: hidden in that actor's list

No existing status values change.

## Backend API Design

### Buyer hide endpoint

- Method: `DELETE`
- Path: `/order/{id}/buyer`

Behavior:
- requires login
- verifies the order exists
- verifies the current user is the buyer
- verifies `status == 3` (completed)
- verifies the order is not already buyer-hidden
- sets `buyer_deleted = 1`

Response semantics:
- success message indicating the order was hidden from the buyer view only

### Seller hide endpoint

- Method: `DELETE`
- Path: `/order/{id}/seller`

Behavior:
- requires login
- verifies the order exists
- verifies the current user is the seller
- verifies `status == 3` (completed)
- verifies the order is not already seller-hidden
- sets `seller_deleted = 1`

Response semantics:
- success message indicating the order was hidden from the seller view only

### Bought list filtering

`GET /order/bought` must filter:
- `buyer_id = currentUserId`
- `buyer_deleted = 0`

### Sold list filtering

`GET /order/sold` must filter:
- `seller_id = currentUserId`
- `seller_deleted = 0`

## Service-Layer Behavior

The service layer must implement a dedicated hide action for each actor instead of reusing cancel / confirm / reject behavior.

Validation rules:
- order missing -> error
- wrong actor -> error
- non-completed order -> error
- already hidden for that actor -> error

State mutation:
- only the relevant visibility flag changes
- no stock restoration
- no product status changes
- no order status changes

## Swagger / OpenAPI Requirements

Swagger must clearly describe:
- the two hide endpoints
- that hiding is actor-scoped only
- that the other party can still see the order
- that the database row remains
- that only completed orders are allowed

The bought and sold list endpoints should also have clearer descriptions mentioning that hidden records are excluded from the current actor's list.

Acceptance for docs:
- `/v3/api-docs` contains both new endpoints
- generated docs include parameter and description text for actor-scoped hiding
- online Swagger / Knife4j page renders the new actions correctly

## Frontend Design

### Order page behavior

On `MyOrdersPage.vue`:

- in the buyer tab:
  - completed orders get a `删除记录` action
  - clicking it confirms intent
  - success removes the record from the current list

- in the seller tab:
  - completed orders get the same `删除记录` action
  - success removes the record from the current list

No button appears for:
- pending shipment
- pending receipt
- cancelled / rejected

### Frontend API client

Add two methods:
- buyer hide order
- seller hide order

The UI should call the actor-specific endpoint based on the active tab instead of guessing on the backend.

## Testing Requirements

### Backend tests

Cover:
- buyer hide success on completed own order
- seller hide success on completed own order
- buyer cannot hide another buyer's order
- seller cannot hide another seller's order
- non-completed order cannot be hidden
- bought list excludes `buyer_deleted = 1`
- sold list excludes `seller_deleted = 1`
- OpenAPI output includes the new endpoints

### Frontend verification

Cover:
- buyer tab shows hide action only on completed orders
- seller tab shows hide action only on completed orders
- successful hide removes the order from the current rendered list
- build passes after API and page changes

### End-to-end verification

Cover:
- create / complete an order
- hide from seller side and confirm seller list excludes it while buyer list still contains it
- hide from buyer side and confirm buyer list excludes it while database row remains

## Non-Goals

This change does not include:
- physical deletion of orders
- hiding cancelled or in-progress orders
- restore / unhide behavior
- admin order recycle bin
- changes to evaluation ownership or visibility rules
