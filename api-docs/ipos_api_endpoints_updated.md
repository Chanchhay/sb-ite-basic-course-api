# IPOS REST API Endpoints — Updated Schema

## 1. Scope

This document redefines the IPOS REST API using the latest database schema.

It covers:

- platform business categories;
- businesses;
- roles and role assignments;
- item groups, units, items, and variants;
- inventory, batches, stock levels, movements, and alerts;
- customers, channel identities, and memberships;
- discounts and coupons;
- orders, payments, QR payments, receipts, and sales;
- notifications;
- bot sessions and channel webhooks;
- service sessions;
- dashboards and reports.

---

## 2. API Conventions

### Base paths

```text
/api/v1
/api/v1/public
/api/v1/admin
/api/v1/businesses/{businessId}
/api/v1/webhooks
```

### Authentication

Protected endpoints require a valid Keycloak access token:

```http
Authorization: Bearer <access-token>
```

### Business ownership

The business owner is resolved from the authenticated Keycloak user.

The API must not trust these values from request bodies:

```text
keycloak_user_id
business_owner_id
created_by
updated_by
processed_by
opened_by
```

### Common query parameters

| Parameter | Description |
|---|---|
| `page` | Page number starting from `0` |
| `size` | Page size |
| `sort` | Example: `createdAt,desc` |
| `keyword` | Search supported text fields |
| `status` | Filter by status |
| `from` | Start date or timestamp |
| `to` | End date or timestamp |

### Update and removal convention

The system uses:

- `POST` to create resources or execute creation commands;
- `GET` to retrieve resources;
- `PUT` to update resources and execute state changes;
- no HTTP `DELETE` endpoints;
- `/delete`, `/archive`, `/cancel`, or `/remove` for logical removal where supported.

### Standard response

```json
{
  "success": true,
  "message": "Request completed successfully",
  "data": {}
}
```

### Paged response

```json
{
  "success": true,
  "message": "Records retrieved successfully",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

---

# 3. Platform Business Categories

`business_categories` classifies businesses. It is separate from item `item_groups`.

## Public endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/public/business-categories` | List business categories |
| `GET` | `/api/v1/public/business-categories/tree` | Get level-1 and level-2 category tree |
| `GET` | `/api/v1/public/business-categories/{categoryId}` | Get business category |
| `GET` | `/api/v1/public/business-categories/slug/{slug}` | Find category by slug |

Filters:

```text
keyword, parentId, level
```

## Administration endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/admin/business-categories` | Create business category |
| `GET` | `/api/v1/admin/business-categories` | Search business categories |
| `GET` | `/api/v1/admin/business-categories/{categoryId}` | Get category details |
| `PUT` | `/api/v1/admin/business-categories/{categoryId}` | Update category |
| `PUT` | `/api/v1/admin/business-categories/{categoryId}/icon` | Update category icon |

---

# 4. Businesses

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/businesses` | Register a business for the authenticated Keycloak user |
| `GET` | `/api/v1/businesses/me` | Get the authenticated owner's business |
| `GET` | `/api/v1/businesses/{businessId}` | Get business details |
| `PUT` | `/api/v1/businesses/{businessId}` | Update business profile |
| `PUT` | `/api/v1/businesses/{businessId}/category` | Change business category |
| `PUT` | `/api/v1/businesses/{businessId}/status` | Change `ACTIVE`, `SUSPENDED`, or `DELETED` status |
| `PUT` | `/api/v1/businesses/{businessId}/enabled` | Enable or disable business access |
| `PUT` | `/api/v1/businesses/{businessId}/listing` | Change public listing visibility |
| `PUT` | `/api/v1/businesses/{businessId}/closed` | Open or close the business |
| `PUT` | `/api/v1/businesses/{businessId}/logo` | Update business logo |
| `PUT` | `/api/v1/businesses/{businessId}/thumbnail` | Update business thumbnail |
| `GET` | `/api/v1/public/businesses` | Search publicly listed businesses |
| `GET` | `/api/v1/public/businesses/{slug}` | Get public business profile by slug |

Public filters:

```text
keyword, categoryId, cityOrProvince, isClosed
```

---

# 5. Roles

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/businesses/{businessId}/roles` | Create role |
| `GET` | `/api/v1/businesses/{businessId}/roles` | List roles |
| `GET` | `/api/v1/businesses/{businessId}/roles/{roleId}` | Get role |
| `PUT` | `/api/v1/businesses/{businessId}/roles/{roleId}` | Update role |
| `PUT` | `/api/v1/businesses/{businessId}/roles/{roleId}/permissions` | Replace role permissions |
| `PUT` | `/api/v1/businesses/{businessId}/roles/{roleId}/delete` | Remove non-system role logically |

Filters:

```text
keyword, isSystem
```

---

# 6. User Role Assignments

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/businesses/{businessId}/user-roles` | Assign role to user |
| `GET` | `/api/v1/businesses/{businessId}/user-roles` | List assignments |
| `GET` | `/api/v1/businesses/{businessId}/user-roles/{assignmentId}` | Get assignment |
| `GET` | `/api/v1/businesses/{businessId}/users/{userId}/roles` | List roles assigned to a user |
| `PUT` | `/api/v1/businesses/{businessId}/user-roles/{assignmentId}` | Change assigned role |
| `PUT` | `/api/v1/businesses/{businessId}/user-roles/{assignmentId}/remove` | Remove assignment |

Filters:

```text
userId, roleId
```

---

# 7. Item Groups

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/businesses/{businessId}/item-groups` | Create item group |
| `GET` | `/api/v1/businesses/{businessId}/item-groups` | List item groups |
| `GET` | `/api/v1/businesses/{businessId}/item-groups/tree` | Get item group tree |
| `GET` | `/api/v1/businesses/{businessId}/item-groups/{itemGroupId}` | Get item group |
| `GET` | `/api/v1/businesses/{businessId}/item-groups/slug/{slug}` | Find item group by slug |
| `PUT` | `/api/v1/businesses/{businessId}/item-groups/{itemGroupId}` | Update item group |
| `PUT` | `/api/v1/businesses/{businessId}/item-groups/{itemGroupId}/parent` | Move item group under another parent |
| `PUT` | `/api/v1/businesses/{businessId}/item-groups/{itemGroupId}/status` | Change item group status |

Filters:

```text
keyword, status, parentId
```

---

# 8. Units

Units are shared reference data because the table has no `business_owner_id`.

## Public/read endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/units` | List active units |
| `GET` | `/api/v1/units/{unitId}` | Get unit |

## Administration endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/admin/units` | Create unit |
| `GET` | `/api/v1/admin/units` | Search all units |
| `PUT` | `/api/v1/admin/units/{unitId}` | Update unit |
| `PUT` | `/api/v1/admin/units/{unitId}/status` | Change unit status |

---

# 9. Items

The latest schema no longer exposes `item_type` or `discount_price`.

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/businesses/{businessId}/items` | Create item |
| `GET` | `/api/v1/businesses/{businessId}/items` | Search items |
| `GET` | `/api/v1/businesses/{businessId}/items/{itemId}` | Get item |
| `GET` | `/api/v1/businesses/{businessId}/items/slug/{slug}` | Find item by slug |
| `GET` | `/api/v1/businesses/{businessId}/items/sku/{sku}` | Find item by SKU |
| `GET` | `/api/v1/businesses/{businessId}/items/code/{code}` | Find item by code |
| `GET` | `/api/v1/businesses/{businessId}/items/barcode/{barcode}` | Find item by barcode |
| `PUT` | `/api/v1/businesses/{businessId}/items/{itemId}` | Update item |
| `PUT` | `/api/v1/businesses/{businessId}/items/{itemId}/availability` | Change item availability |
| `PUT` | `/api/v1/businesses/{businessId}/items/{itemId}/image` | Update item image |
| `GET` | `/api/v1/public/businesses/{businessId}/items` | List available public items |
| `GET` | `/api/v1/public/businesses/{businessId}/items/{itemId}` | Get public item |

Filters:

```text
keyword, itemGroupId, unitId, availability, minPrice, maxPrice
```

---

# 10. Item Variants

The latest variant table contains no barcode or status field.

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/businesses/{businessId}/items/{itemId}/variants` | Create variant |
| `GET` | `/api/v1/businesses/{businessId}/items/{itemId}/variants` | List item variants |
| `GET` | `/api/v1/businesses/{businessId}/item-variants/{variantId}` | Get variant |
| `PUT` | `/api/v1/businesses/{businessId}/item-variants/{variantId}` | Update variant |

---

# 11. Inventory Batches

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/businesses/{businessId}/batches` | Create batch |
| `GET` | `/api/v1/businesses/{businessId}/batches` | Search batches |
| `GET` | `/api/v1/businesses/{businessId}/batches/{batchId}` | Get batch |
| `PUT` | `/api/v1/businesses/{businessId}/batches/{batchId}` | Update batch metadata |
| `PUT` | `/api/v1/businesses/{businessId}/batches/{batchId}/status` | Change batch status |

Filters:

```text
itemId, batchNo, status, expiryFrom, expiryTo
```

---

# 12. Stock Levels

Stock quantity must be changed through inventory operations, not through a direct quantity update.

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/businesses/{businessId}/stock-levels` | List current stock levels |
| `GET` | `/api/v1/businesses/{businessId}/stock-levels/{stockLevelId}` | Get stock level |
| `GET` | `/api/v1/businesses/{businessId}/items/{itemId}/stock-levels` | Get stock for item |
| `GET` | `/api/v1/businesses/{businessId}/locations/{locationId}/stock-levels` | Get stock at location |
| `PUT` | `/api/v1/businesses/{businessId}/stock-levels/{stockLevelId}/threshold` | Update low-stock threshold |

Filters:

```text
itemId, locationId, lowStock, outOfStock
```

---

# 13. Stock-In Items

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/businesses/{businessId}/stock-in-items` | Add stock-in item and create stock movement |
| `POST` | `/api/v1/businesses/{businessId}/stock-in-items/bulk` | Add multiple stock-in items |
| `GET` | `/api/v1/businesses/{businessId}/stock-in-items` | Search stock-in items |
| `GET` | `/api/v1/businesses/{businessId}/stock-in-items/{itemId}` | Get stock-in item |

Filters:

```text
itemId, batchId, unitId, from, to
```

---

# 14. Stock-Out Items

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/businesses/{businessId}/stock-out-items` | Add stock-out item and create stock movement |
| `POST` | `/api/v1/businesses/{businessId}/stock-out-items/bulk` | Add multiple stock-out items |
| `GET` | `/api/v1/businesses/{businessId}/stock-out-items` | Search stock-out items |
| `GET` | `/api/v1/businesses/{businessId}/stock-out-items/{itemId}` | Get stock-out item |

Filters:

```text
itemId, batchId, unitId, from, to
```

---

# 15. Stock Movements

`stock_movements` is an append-only inventory ledger.

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/businesses/{businessId}/stock-movements` | Search stock movements |
| `GET` | `/api/v1/businesses/{businessId}/stock-movements/{movementId}` | Get stock movement |
| `GET` | `/api/v1/businesses/{businessId}/items/{itemId}/stock-movements` | Get movement history for item |
| `POST` | `/api/v1/businesses/{businessId}/inventory/adjustments` | Create inventory adjustment |
| `POST` | `/api/v1/businesses/{businessId}/inventory/transfers` | Record transfer-out and transfer-in movements |
| `POST` | `/api/v1/businesses/{businessId}/inventory/returns` | Record stock return movement |

Filters:

```text
itemId, batchId, locationType, locationId, type, refOrderId, from, to
```

There are no update or delete endpoints for stock movements.

---

# 16. Inventory Alerts

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/businesses/{businessId}/inventory-alerts` | List alerts |
| `GET` | `/api/v1/businesses/{businessId}/inventory-alerts/{alertId}` | Get alert |
| `PUT` | `/api/v1/businesses/{businessId}/inventory-alerts/{alertId}/acknowledge` | Acknowledge alert |
| `PUT` | `/api/v1/businesses/{businessId}/inventory-alerts/{alertId}/resolve` | Resolve alert |
| `PUT` | `/api/v1/businesses/{businessId}/inventory-alerts/{alertId}/reopen` | Reopen alert |

Filters:

```text
alertType, status, itemId, branchId, batchId
```

---

# 17. Global Customers

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/global-customers` | Create global customer from authenticated Keycloak user |
| `GET` | `/api/v1/global-customers/me` | Get authenticated global customer |
| `GET` | `/api/v1/global-customers/{globalCustomerId}` | Get global customer |
| `GET` | `/api/v1/global-customers/keycloak/{keycloakUserId}` | Find global customer by Keycloak ID |
| `PUT` | `/api/v1/global-customers/{globalCustomerId}` | Update global customer |

---

# 18. Business Customers

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/businesses/{businessId}/customers` | Create customer |
| `GET` | `/api/v1/businesses/{businessId}/customers` | Search customers |
| `GET` | `/api/v1/businesses/{businessId}/customers/{customerId}` | Get customer |
| `PUT` | `/api/v1/businesses/{businessId}/customers/{customerId}` | Update customer |
| `PUT` | `/api/v1/businesses/{businessId}/customers/{customerId}/membership` | Change current membership tier |
| `GET` | `/api/v1/businesses/{businessId}/customers/{customerId}/orders` | Get customer orders |
| `GET` | `/api/v1/businesses/{businessId}/customers/{customerId}/sales` | Get customer sales |
| `GET` | `/api/v1/businesses/{businessId}/customers/{customerId}/spending` | Get spending summary |

Filters:

```text
keyword, globalCustomerId, membershipTierId, minTotalSpend
```

---

# 19. Customer Channel Identities

The current table stores the channel type but does not store an external channel identifier.

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/businesses/{businessId}/customers/{customerId}/channel-identities` | Link channel type to customer |
| `GET` | `/api/v1/businesses/{businessId}/customers/{customerId}/channel-identities` | List customer channel identities |
| `GET` | `/api/v1/businesses/{businessId}/channel-identities` | Search channel identities |
| `PUT` | `/api/v1/businesses/{businessId}/channel-identities/{identityId}` | Update channel identity |
| `PUT` | `/api/v1/businesses/{businessId}/channel-identities/{identityId}/unlink` | Unlink identity |

Filters:

```text
customerId, channel
```

---

# 20. Membership Types

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/businesses/{businessId}/membership-types` | Create membership type |
| `GET` | `/api/v1/businesses/{businessId}/membership-types` | List membership types |
| `GET` | `/api/v1/businesses/{businessId}/membership-types/{membershipTypeId}` | Get membership type |
| `PUT` | `/api/v1/businesses/{businessId}/membership-types/{membershipTypeId}` | Update membership type |
| `PUT` | `/api/v1/businesses/{businessId}/membership-types/{membershipTypeId}/discount` | Assign discount |
| `PUT` | `/api/v1/businesses/{businessId}/membership-types/{membershipTypeId}/status` | Change status |

Filters:

```text
keyword, status, discountId
```

---

# 21. Customer Membership History

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/businesses/{businessId}/customer-memberships` | Create membership record |
| `GET` | `/api/v1/businesses/{businessId}/customer-memberships` | Search membership records |
| `GET` | `/api/v1/businesses/{businessId}/customers/{customerId}/memberships` | Get customer membership history |
| `GET` | `/api/v1/businesses/{businessId}/customer-memberships/{membershipId}` | Get membership record |
| `PUT` | `/api/v1/businesses/{businessId}/customer-memberships/{membershipId}/activate` | Activate membership |
| `PUT` | `/api/v1/businesses/{businessId}/customer-memberships/{membershipId}/deactivate` | Deactivate membership |

---

# 22. Discounts

The updated schema adds:

```text
scope = MEMBERSHIP
selected_days
```

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/businesses/{businessId}/discounts` | Create discount |
| `GET` | `/api/v1/businesses/{businessId}/discounts` | Search discounts |
| `GET` | `/api/v1/businesses/{businessId}/discounts/{discountId}` | Get discount |
| `PUT` | `/api/v1/businesses/{businessId}/discounts/{discountId}` | Update discount |
| `PUT` | `/api/v1/businesses/{businessId}/discounts/{discountId}/schedule` | Update dates and selected days |
| `PUT` | `/api/v1/businesses/{businessId}/discounts/{discountId}/status` | Change discount status |
| `POST` | `/api/v1/businesses/{businessId}/discounts/evaluate` | Evaluate automatic discounts |
| `POST` | `/api/v1/businesses/{businessId}/discounts/preview` | Preview discount calculation |

Filters:

```text
keyword, type, ruleType, scope, status, activeAt, selectedDay
```

---

# 23. Coupons

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/businesses/{businessId}/coupons` | Create coupon |
| `GET` | `/api/v1/businesses/{businessId}/coupons` | Search coupons |
| `GET` | `/api/v1/businesses/{businessId}/coupons/{couponId}` | Get coupon |
| `GET` | `/api/v1/businesses/{businessId}/coupons/code/{code}` | Find coupon by code |
| `POST` | `/api/v1/businesses/{businessId}/coupons/validate` | Validate coupon for customer/order |
| `PUT` | `/api/v1/businesses/{businessId}/coupons/{couponId}` | Update coupon |
| `PUT` | `/api/v1/businesses/{businessId}/coupons/{couponId}/status` | Change coupon status |

Filters:

```text
keyword, discountId, status, activeAt
```

---

# 24. Orders

The current order states are:

```text
PENDING
PAID
FAILED
CANCELLED
```

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/businesses/{businessId}/orders` | Create pending order |
| `GET` | `/api/v1/businesses/{businessId}/orders` | Search orders |
| `GET` | `/api/v1/businesses/{businessId}/orders/{orderId}` | Get order |
| `GET` | `/api/v1/businesses/{businessId}/orders/invoice/{invoiceNumber}` | Find order by invoice |
| `PUT` | `/api/v1/businesses/{businessId}/orders/{orderId}` | Update pending order |
| `POST` | `/api/v1/businesses/{businessId}/orders/{orderId}/items` | Add order item |
| `GET` | `/api/v1/businesses/{businessId}/orders/{orderId}/items` | List order items |
| `GET` | `/api/v1/businesses/{businessId}/order-items/{itemId}` | Get order item |
| `PUT` | `/api/v1/businesses/{businessId}/orders/{orderId}/items/{itemId}` | Update order item |
| `PUT` | `/api/v1/businesses/{businessId}/orders/{orderId}/items/{itemId}/remove` | Remove order item |
| `POST` | `/api/v1/businesses/{businessId}/orders/{orderId}/discounts/apply` | Apply automatic discount |
| `POST` | `/api/v1/businesses/{businessId}/orders/{orderId}/coupons/apply` | Apply coupon |
| `PUT` | `/api/v1/businesses/{businessId}/orders/{orderId}/paid` | Mark order paid after successful payment |
| `PUT` | `/api/v1/businesses/{businessId}/orders/{orderId}/failed` | Mark order failed |
| `PUT` | `/api/v1/businesses/{businessId}/orders/{orderId}/cancel` | Cancel order |

Filters:

```text
keyword, invoiceNumber, customerId, cashierId, channel, status, from, to
```

There is no refund endpoint because the latest schema has no refunded order or payment status.

---

# 25. Payments

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/businesses/{businessId}/orders/{orderId}/payments` | Create pending payment |
| `GET` | `/api/v1/businesses/{businessId}/payments` | Search payments |
| `GET` | `/api/v1/businesses/{businessId}/payments/{paymentId}` | Get payment |
| `GET` | `/api/v1/businesses/{businessId}/orders/{orderId}/payments` | List order payments |
| `GET` | `/api/v1/businesses/{businessId}/payments/reference/{providerReference}` | Find payment by provider reference |
| `PUT` | `/api/v1/businesses/{businessId}/payments/{paymentId}/success` | Mark successful |
| `PUT` | `/api/v1/businesses/{businessId}/payments/{paymentId}/failed` | Mark failed |

Filters:

```text
orderId, methodType, status, providerReference, from, to
```

---

# 26. Payment QR Codes

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/businesses/{businessId}/orders/{orderId}/payment-qr-codes` | Generate QR payment |
| `GET` | `/api/v1/businesses/{businessId}/payment-qr-codes` | Search QR payments |
| `GET` | `/api/v1/businesses/{businessId}/payment-qr-codes/{qrCodeId}` | Get QR payment |
| `GET` | `/api/v1/businesses/{businessId}/payment-qr-codes/{qrCodeId}/image` | Render QR image |
| `GET` | `/api/v1/payment-qr-codes/hash/{md5Hash}` | Find QR by provider hash |
| `PUT` | `/api/v1/businesses/{businessId}/payment-qr-codes/{qrCodeId}/paid` | Mark QR paid |
| `PUT` | `/api/v1/businesses/{businessId}/payment-qr-codes/{qrCodeId}/expire` | Mark QR expired |
| `PUT` | `/api/v1/businesses/{businessId}/payment-qr-codes/{qrCodeId}/cancel` | Cancel QR |

Filters:

```text
orderId, paymentId, provider, status, from, to
```

---

# 27. Payment Webhooks

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/webhooks/payments/{provider}` | Receive payment webhook |

Webhook processing must:

- verify provider authenticity;
- locate the QR/payment using hash or provider reference;
- process duplicate events idempotently;
- update payment, QR, order, and sale atomically.

---

# 28. Receipts

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/businesses/{businessId}/orders/{orderId}/receipts` | Generate receipt |
| `GET` | `/api/v1/businesses/{businessId}/receipts` | Search receipts |
| `GET` | `/api/v1/businesses/{businessId}/receipts/{receiptId}` | Get receipt |
| `GET` | `/api/v1/businesses/{businessId}/receipts/{receiptId}/file` | Retrieve receipt file |
| `POST` | `/api/v1/businesses/{businessId}/receipts/{receiptId}/resend` | Resend digital receipt |

Filters:

```text
orderId, type, invoiceNumber, from, to
```

---

# 29. Sales

Sales are generated when an order is successfully paid.

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/businesses/{businessId}/sales` | Search sales |
| `GET` | `/api/v1/businesses/{businessId}/sales/{saleId}` | Get sale |
| `GET` | `/api/v1/businesses/{businessId}/sales/order/{orderId}` | Find sale by order |
| `GET` | `/api/v1/businesses/{businessId}/sales/invoice/{invoiceNumber}` | Find sale by invoice |

Filters:

```text
customerId, cashierId, channel, paymentMethod, from, to
```

There is no public create or update endpoint for sales. Sales are created internally from successful order-payment processing.

---

# 30. Notification Senders

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/businesses/{businessId}/notifications` | Send notification |
| `GET` | `/api/v1/businesses/{businessId}/notifications/sent` | List sent notifications |
| `GET` | `/api/v1/businesses/{businessId}/notifications/sent/{notificationId}` | Get sent notification |
| `PUT` | `/api/v1/businesses/{businessId}/notifications/sent/{notificationId}/delete` | Soft-delete sender copy |

Filters:

```text
type, senderId, from, to
```

---

# 31. Notification Receivers

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/businesses/{businessId}/notifications/inbox` | List received notifications |
| `GET` | `/api/v1/businesses/{businessId}/notifications/inbox/{receiverRecordId}` | Get received notification |
| `GET` | `/api/v1/businesses/{businessId}/notifications/unread-count` | Get unread count |
| `PUT` | `/api/v1/businesses/{businessId}/notifications/inbox/{receiverRecordId}/read` | Mark read |
| `PUT` | `/api/v1/businesses/{businessId}/notifications/inbox/read-all` | Mark all read |
| `PUT` | `/api/v1/businesses/{businessId}/notifications/inbox/{receiverRecordId}/delete` | Soft-delete receiver copy |

Filters:

```text
type, receiverId, isRead, from, to
```

---

# 32. Bot Sessions

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/businesses/{businessId}/bot-sessions` | Search bot sessions |
| `GET` | `/api/v1/businesses/{businessId}/bot-sessions/{sessionId}` | Get session |
| `PUT` | `/api/v1/businesses/{businessId}/bot-sessions/{sessionId}` | Update session state/context |
| `PUT` | `/api/v1/businesses/{businessId}/bot-sessions/{sessionId}/customer` | Link customer |
| `PUT` | `/api/v1/businesses/{businessId}/bot-sessions/{sessionId}/reset` | Reset session |

Filters:

```text
channel, externalId, customerId, state
```

---

# 33. Telegram and Messenger Webhooks

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/webhooks/telegram/{businessId}` | Receive Telegram update |
| `GET` | `/api/v1/webhooks/messenger/{businessId}` | Verify Messenger webhook |
| `POST` | `/api/v1/webhooks/messenger/{businessId}` | Receive Messenger event |

Webhook handlers should create or update `bot_sessions` internally.

---

# 34. Service Sessions

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/businesses/{businessId}/service-sessions` | Open service session |
| `GET` | `/api/v1/businesses/{businessId}/service-sessions` | Search service sessions |
| `GET` | `/api/v1/businesses/{businessId}/service-sessions/{sessionId}` | Get service session |
| `PUT` | `/api/v1/businesses/{businessId}/service-sessions/{sessionId}` | Update running session |
| `PUT` | `/api/v1/businesses/{businessId}/service-sessions/{sessionId}/close` | Close session and calculate billing |
| `PUT` | `/api/v1/businesses/{businessId}/service-sessions/{sessionId}/cancel` | Cancel session |
| `POST` | `/api/v1/businesses/{businessId}/service-sessions/{sessionId}/order` | Generate or attach order |

Filters:

```text
customerId, orderId, status, from, to
```

---

# 35. Dashboard

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/businesses/{businessId}/dashboard/summary` | Main dashboard summary |
| `GET` | `/api/v1/businesses/{businessId}/dashboard/sales-chart` | Sales chart |
| `GET` | `/api/v1/businesses/{businessId}/dashboard/top-items` | Top items |
| `GET` | `/api/v1/businesses/{businessId}/dashboard/recent-orders` | Recent orders |
| `GET` | `/api/v1/businesses/{businessId}/dashboard/inventory-alerts` | Alert summary |
| `GET` | `/api/v1/businesses/{businessId}/dashboard/payment-summary` | Payment summary |
| `GET` | `/api/v1/businesses/{businessId}/dashboard/customer-summary` | Customer summary |

---

# 36. Reports

## Sales reports

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/businesses/{businessId}/reports/sales/summary` | Sales summary |
| `GET` | `/api/v1/businesses/{businessId}/reports/sales/daily` | Daily sales |
| `GET` | `/api/v1/businesses/{businessId}/reports/sales/by-item` | Sales by item |
| `GET` | `/api/v1/businesses/{businessId}/reports/sales/by-item group` | Sales by item item group |
| `GET` | `/api/v1/businesses/{businessId}/reports/sales/by-channel` | Sales by channel |
| `GET` | `/api/v1/businesses/{businessId}/reports/sales/by-cashier` | Sales by cashier |
| `GET` | `/api/v1/businesses/{businessId}/reports/profit` | Revenue, cost, and profit |

## Inventory reports

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/businesses/{businessId}/reports/inventory/summary` | Inventory summary |
| `GET` | `/api/v1/businesses/{businessId}/reports/inventory/valuation` | Stock valuation |
| `GET` | `/api/v1/businesses/{businessId}/reports/inventory/low-stock` | Low-stock report |
| `GET` | `/api/v1/businesses/{businessId}/reports/inventory/out-of-stock` | Out-of-stock report |
| `GET` | `/api/v1/businesses/{businessId}/reports/inventory/expiry` | Expiry report |
| `GET` | `/api/v1/businesses/{businessId}/reports/inventory/movements` | Movement report |

## Customer reports

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/businesses/{businessId}/reports/customers/top-spending` | Top-spending customers |
| `GET` | `/api/v1/businesses/{businessId}/reports/customers/memberships` | Membership report |
| `GET` | `/api/v1/businesses/{businessId}/reports/customers/orders` | Customer order report |

## Payment reports

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/businesses/{businessId}/reports/payments/summary` | Payment summary |
| `GET` | `/api/v1/businesses/{businessId}/reports/payments/by-method` | Payments by method |
| `GET` | `/api/v1/businesses/{businessId}/reports/payments/failed` | Failed payments |
| `GET` | `/api/v1/businesses/{businessId}/reports/payments/pending` | Pending payments |

Common report filters:

```text
from, to, groupBy, channel, customerId, cashierId,
itemId, itemGroupId, locationId
```

---

# 37. Media Upload

The database stores media URLs but has no file metadata table.

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/media/images` | Upload one image |
| `POST` | `/api/v1/media/images/bulk` | Upload multiple images |
| `GET` | `/api/v1/media/{objectKey}` | Retrieve media |
| `PUT` | `/api/v1/media/{objectKey}/delete` | Remove media from storage |

Supported uses:

```text
business logo
business thumbnail
business category icon
item image
receipt file
```

---

# 38. Platform Administration

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/admin/businesses` | Search all businesses |
| `GET` | `/api/v1/admin/businesses/{businessId}` | Get full business details |
| `PUT` | `/api/v1/admin/businesses/{businessId}/suspend` | Suspend business |
| `PUT` | `/api/v1/admin/businesses/{businessId}/activate` | Activate business |
| `PUT` | `/api/v1/admin/businesses/{businessId}/delete` | Mark business deleted |
| `GET` | `/api/v1/admin/dashboard/summary` | Platform dashboard |
| `GET` | `/api/v1/admin/reports/businesses` | Business usage report |
| `GET` | `/api/v1/admin/reports/sales` | Platform sales report |

---

# 39. HTTP Status Codes

| Status | Usage |
|---|---|
| `200 OK` | Successful read or update |
| `201 Created` | Resource created |
| `204 No Content` | Successful action with no response body |
| `400 Bad Request` | Validation error |
| `401 Unauthorized` | Missing or invalid token |
| `403 Forbidden` | Insufficient permission or business access |
| `404 Not Found` | Resource not found within business scope |
| `409 Conflict` | Duplicate or invalid state transition |
| `422 Unprocessable Entity` | Domain-rule violation |
| `500 Internal Server Error` | Unexpected server error |

---

# 40. Business Isolation Rules

Every business-owned query must include the current business ID.

Incorrect:

```text
findById(resourceId)
```

Required:

```text
findByIdAndBusinessOwnerId(resourceId, businessId)
```

Every relationship must remain inside one business:

```text
role.business == userRole.business
item.item group.business == item.business
variant.item.business == variant.business
batch.item.business == batch.business
order.customer.business == order.business
orderItem.order.business == orderItem.business
orderItem.item.business == orderItem.business
payment.order.business == payment.business
coupon.discount.business == coupon.business
receipt.order.business == receipt.business
sale.order.business == sale.business
```

---

# 41. Current Schema Limitations Affecting APIs

These are schema constraints, not additional endpoint requirements.

1. `item_groups.parent_id` is marked unique, which allows only one child for each parent.
2. `customer_channel_identities` has no `external_id`, so Telegram and Messenger identities cannot be uniquely stored.
3. `discounts` supports `CATEGORY`, `ITEM`, and `MEMBERSHIP` scopes but has no target table or target ID.
4. `stock_levels.location_id` has no `location_type`, while stock movements do.
5. Branch and warehouse tables are not present.
6. Stock-in and stock-out headers are not present; only item tables exist.
7. `stock_movements` contains references to stock-in, stock-out, return, and transfer records whose parent tables are not present.
8. `membership_types.discount_type` behaves as a discount foreign key even though its name suggests an enum.
9. `bot_sessions.cart_id` exists, but no cart table is present.
10. The schema has no refunded order or payment status, so refund endpoints are intentionally excluded.

---

# 42. Recommended Implementation Order

1. Business item groups
2. Businesses
3. Roles and user-role assignments
4. Item item groups and units
5. Items and item variants
6. Customers and memberships
7. Discounts and coupons
8. Batches and stock levels
9. Stock-in, stock-out, and stock movements
10. Orders and order items
11. Payments and payment QR codes
12. Sales and receipts
13. Notifications
14. Bot sessions and webhooks
15. Service sessions
16. Dashboard and reports
