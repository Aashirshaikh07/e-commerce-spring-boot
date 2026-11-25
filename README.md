<h1 align="center">E-commerce Backend</h1>

<p align="center">
  <strong>An event-driven Spring Boot API for the storefront</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot 4.1" />
  <img src="https://img.shields.io/badge/Spring%20Security-JWT-6DB33F?logo=springsecurity&logoColor=white" alt="Spring Security and JWT" />
  <img src="https://img.shields.io/badge/Apache%20Kafka-Events-231F20?logo=apachekafka&logoColor=white" alt="Apache Kafka" />
  <img src="https://img.shields.io/badge/MySQL-Persistence-4479A1?logo=mysql&logoColor=white" alt="MySQL" />
</p>

---

## Introduction

The E-commerce Backend owns customer accounts, saved addresses, the product catalog, inventory, and orders. Shoppers can sign in, recover their accounts, browse products, and track orders. Administrators manage products, stock, and order status through role-protected APIs.

When a shopper checks out, the backend prices the order from the catalog and deducts stock. It saves the pending order and its outgoing event in the same MySQL transaction using a transactional outbox. Payment outcomes arrive through Kafka and drive order confirmation or cancellation. A Spring event listener publishes the corresponding notification after the order transaction commits.

> **Checkout recovery verified in Razorpay Test Mode.** With the browser's payment verification request blocked, Razorpay delivered a capture webhook and the order reached `CONFIRMED`.

## System Overview

The React storefront uses the e-commerce API for accounts, product browsing, and orders. It calls the payment service to start Razorpay checkout and check payment status. Razorpay sends signed webhooks to the payment service. Kafka carries order and payment events between the backends, and the notification service consumes Kafka events to send email.

![The React storefront calls the e-commerce and payment APIs. Kafka carries order creation, payment outcomes, and notification events. Razorpay calls the payment service webhook, and the notification service delivers email.](docs/images/ecommerce-system.svg)

| Component | Responsibility |
|---|---|
| **E-commerce Backend** | This repository. Owns customer accounts, saved addresses, the catalog, inventory, and orders. |
| [Payment Service](https://github.com/Aashirshaikh07/payment-service) | Creates Razorpay checkout orders, reconciles captures from callbacks and webhooks, and publishes payment outcomes. |
| [Notification Service](https://github.com/Aashirshaikh07/notification-service) | Consumes notification events and delivers welcome, password reset, and order emails. |
| [React Storefront](https://github.com/Aashirshaikh07/e-commerce-frontend) | Provides product browsing, account screens, checkout, and order history. The repository is private. |

## Order Lifecycle

### Place the order

The storefront calls `POST /api/v1/orders` with product IDs, quantities, and an `Idempotency-Key`. The backend looks up the products, checks available stock, and calculates the total using catalog prices. It commits the `PENDING` order, line items, stock deduction, idempotency record, and order-created outbox entry in one MySQL transaction. [Order creation transaction](src/main/java/com/aashir/ecommerce/service/OrderCreationTransactionService.java)

### Retry order creation

If MySQL commits the order and the browser loses the response, the storefront can retry the same request with its original key. The backend looks up the key for the signed-in customer and compares a SHA-256 fingerprint of the product IDs and quantities, sorted by product ID.

| Request | Result |
|---|---|
| First valid request with a new key | `201 Created` with a new order. |
| Same customer, key, and fingerprint | `200 OK` with the existing order. The requests result in one stock deduction and one order-created outbox entry. |
| Same customer and key, different fingerprint | `409 Conflict`. The key is already associated with a different request. |

MySQL enforces uniqueness on `(user_id, idempotency_key)`. The transaction claims the key before touching stock. If two matching requests arrive together and one hits the unique constraint, the service can recover the order created by the other request. A fresh key represents a new order request. [Retry handling](src/main/java/com/aashir/ecommerce/service/OrderService.java#L61) · [Request fingerprint](src/main/java/com/aashir/ecommerce/service/OrderRequestFingerprint.java) · [Unique key](src/main/java/com/aashir/ecommerce/entity/OrderIdempotency.java)

### Open checkout and verify payment

The order outbox publishes to `order-events`, which the payment service consumes to prepare a payment. The storefront calls `POST /payments/{orderId}/checkout` with a separate key, `checkout-{orderId}`. A replay of a completed checkout returns the existing Razorpay order.

After the Razorpay callback, the storefront calls `POST /payments/verify`. For a new verification, the payment service checks that the payment belongs to the customer, validates the callback signature, and fetches the payment from Razorpay. It checks the order and payment IDs, amount, currency, and capture status before recording success. A signed `payment.captured` webhook can also lead to reconciliation if the tab closes or verification fails. [Payment verification](https://github.com/Aashirshaikh07/payment-service/blob/main/src/main/java/com/aashir/payment/service/PaymentVerificationService.java#L33) · [Webhook reconciliation](https://github.com/Aashirshaikh07/payment-service/blob/main/src/main/java/com/aashir/payment/service/WebhookPaymentReconciliationService.java#L29)

### Apply the payment outcome

The payment service writes the capture and a success outbox entry together. When `payment-succeeded` reaches the order backend, the consumer locks the order row and checks `processed_events`. For a new success on a pending order, it moves the order to `CONFIRMED` and records the event ID in the same transaction. The Spring notification listener hands off the confirmation email event after commit. A replay of the same Kafka event skips the update and notification.

The normal order progression is `PENDING → CONFIRMED → SHIPPED → DELIVERED`. A `payment-failed` event can cancel a pending order. The cancellation transaction restores stock. If a capture arrives after cancellation, the order stays `CANCELLED` and the consumer saves one `late_payment_reviews` entry per order and payment. [Payment consumer](src/main/java/com/aashir/ecommerce/kafka/PaymentKafkaConsumer.java) · [Order state rules](src/main/java/com/aashir/ecommerce/entity/OrderStatus.java)

## Catalog and Inventory

### Browse the catalog

`GET /api/v1/products` lists active products. Shoppers can search by keyword in the product name and description, filter by category and price range, and sort by `price`, `name`, or `createdAt`. Pages start at `0` and default to 20 products, with a maximum page size of 40.

```http
GET /api/v1/products?category=electronics&minPrice=1000&sortBy=price&direction=asc&page=0&size=20
```

The default all-category view takes one product from each category per pass before slicing the results into pages. Each category keeps its newest-first order. Selecting a category or another sort uses database pagination with product ID as the tie-breaker. [Catalog query](src/main/java/com/aashir/ecommerce/service/ProductService.java#L109) · [Category mixing](src/main/java/com/aashir/ecommerce/service/ProductMixingService.java)

### Load a product page

`GET /api/v1/products/{id}` returns the product’s name, price, description, image URL, highlights, and grouped specifications, along with `stockQuantity` and `inStock`. The backend sets `inStock` when the product is active and has a positive available quantity. Administrators maintain highlights and specifications through `PUT /api/v1/products/{productId}/details`. [Product page response](src/main/java/com/aashir/ecommerce/dto/ProductPageDetailsResponse.java) · [Product details API](src/main/java/com/aashir/ecommerce/controller/ProductDetailsController.java)

### Manage products and stock

When an administrator creates a product, the service creates its inventory record from the submitted `stockQuantity`. Administrators can update core catalog fields. The delete endpoint sets the product to `INACTIVE`, taking it out of the catalog listing. [Catalog administration](src/main/java/com/aashir/ecommerce/controller/ProductController.java)

Inventory routes require the `ADMIN` role.

| Endpoint | Operation |
|---|---|
| `GET /api/v1/inventory/{productId}` | Read the current quantity. |
| `POST /api/v1/inventory/{productId}` | Initialize stock for a product without an inventory record. |
| `PATCH /api/v1/inventory/{productId}/add` | Increase stock by the requested quantity. |
| `PATCH /api/v1/inventory/{productId}/remove` | Decrease stock. The service rejects a quantity larger than the available stock. |

Availability can change between browsing and checkout. The order transaction checks the product’s active status and available quantity again before deducting stock. [Inventory service](src/main/java/com/aashir/ecommerce/service/InventoryService.java) · [Order creation transaction](src/main/java/com/aashir/ecommerce/service/OrderCreationTransactionService.java)

## Accounts and Security

### Registration and sign-in

`POST /auth/register` requires a name, a valid email, a 10-digit phone number, and an 8–72 character password with matching confirmation. The service assigns the `USER` role and stores a BCrypt password hash. MySQL enforces uniqueness on email and phone. The API returns `201 Created` for a new account and `409 Conflict` for a duplicate email or phone number. [Registration service](src/main/java/com/aashir/ecommerce/service/AuthService.java#L32) · [Registration validation](src/main/java/com/aashir/ecommerce/dto/userdto/RegisterRequest.java) · [User model](src/main/java/com/aashir/ecommerce/entity/User.java)

`POST /auth/login` authenticates the account and returns a signed bearer JWT with the email, user ID, and role. The token expires after **one hour**. Spring Security runs in stateless mode. For protected routes, the storefront sends `Authorization: Bearer <token>`. The JWT filter validates the token and loads the account’s current authorities for permission checks. An expired token returns `401 Unauthorized`. [Authentication API](src/main/java/com/aashir/ecommerce/controller/AuthController.java) · [JWT filter](src/main/java/com/aashir/ecommerce/security/JwtAuthenticationFilter.java)

### Access rules and saved addresses

| Access | Examples |
|---|---|
| Public | Register, sign in, request a password reset, and browse the catalog. |
| `USER` | Place orders, view your own order history, and manage saved addresses. |
| `ADMIN` | Manage products and inventory, and update order status. |

`GET /api/v1/addresses` lists the signed-in customer’s addresses. `POST /api/v1/addresses` adds an address, up to a limit of **10** per customer. `PUT` and `DELETE` on `/api/v1/addresses/{id}` look up the record by both the address ID and the signed-in user’s ID. A lookup with no match returns `404 Not Found`. [Address API](src/main/java/com/aashir/ecommerce/controller/AddressController.java) · [Address service](src/main/java/com/aashir/ecommerce/service/AddressService.java)

The local CORS policy allows the storefront at `http://localhost:5173`, including requests with `Authorization` and `Idempotency-Key` headers. [Security configuration](src/main/java/com/aashir/ecommerce/config/SecurityConfig.java)

### Password reset

1. **Request a code.** `POST /auth/forgot-password` accepts `email` and returns a generic acknowledgement. For an eligible active account, the service generates a six-digit one-time password, stores its BCrypt hash, and triggers email delivery through a Spring listener after the transaction commits.
2. **Verify the code.** `POST /auth/verify-otp` accepts `email` and `otp`. A valid code returns `resetToken`. The service stores a SHA-256 hash of the token and records failed code attempts even when verification returns an error.
3. **Set a new password.** `POST /auth/reset-password` accepts `resetToken` and `newPassword`. The service checks the token hash and expiry, updates the password with BCrypt, and marks the reset record `USED`.

The OTP and reset token each expire after **10 minutes** by default. **Five failed attempts** invalidate the code. After the **60-second resend cooldown**, issuing a new code invalidates earlier active or verified reset records. [Password reset service](src/main/java/com/aashir/ecommerce/service/PasswordResetService.java) · [Reset settings](src/main/resources/application.properties#L46) · [After-commit listener](src/main/java/com/aashir/ecommerce/service/NotificationEventListener.java)
