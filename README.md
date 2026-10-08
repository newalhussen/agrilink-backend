# AgriLink Backend

Spring Boot REST API for AgriLink, the Ethiopian agricultural trading and logistics platform connecting
farmers and cooperatives, buyers and businesses, drivers, and operations staff.

The core flow, all implemented end to end:

```
Farmer lists produce -> Buyer orders -> Farmer accepts -> Buyer pays (escrow) -> Driver accepts the job
-> Driver collects (pickup code) -> Driver delivers (delivery code) -> Buyer confirms -> Money released
```

Java 21 · Spring Boot 4 · Spring Security (JWT) · Spring Data JPA / Hibernate · PostgreSQL · Flyway · Maven.

## Quick start

Prerequisites: JDK 21+ and a local PostgreSQL (no Docker needed).

```powershell
# 1. Create the database once (adjust the superuser to yours)
psql -U postgres -f scripts/create-database.sql

# 2. Point the app at it (defaults: localhost:5432/agrilink, user postgres)
$env:AGRILINK_DB_USERNAME = "postgres"
$env:AGRILINK_DB_PASSWORD = "<your password>"

# 3. Run with the dev profile (seeds demo data, exposes OTPs, mock payments confirm instantly)
./mvnw spring-boot:run "-Dspring-boot.run.profiles=dev"      # or: mvn spring-boot:run ...
```

The API is at `http://localhost:8080/api/v1`, interactive docs at `/swagger-ui.html`, the OpenAPI document at
`/api-docs` (generate Kotlin / Swift / TypeScript clients from it).

Dev profile accounts (password `Demo@12345`, admin `Admin@12345`):

| Role   | Phone         | Notes                                        |
|--------|---------------|----------------------------------------------|
| Admin  | 0900000000    | operations dashboard                         |
| Farmer | 0911000001    | Tolosa Bekele, Meki Fruit Co-op (tomato, onion) |
| Farmer | 0911000002    | Ayantu Gemechu, Ziway (onion, potato)         |
| Buyer  | 0922000001    | Habesha Kitchen, Bole (restaurant)            |
| Driver | 0933000001    | Abebe Tadesse, Isuzu FSR 5 t, available       |

Mock payment accounts: any number works; a number ending in `0000` fails, to exercise the "payment failed" screens.

### Embedded database (no PostgreSQL installed)

To run the backend for client work on a machine without PostgreSQL, a test-scope launcher starts the app on a
throw-away embedded PostgreSQL (data kept in `target/dev-db`) with the dev profile:

```powershell
mvn -q test-compile dependency:build-classpath "-Dmdep.outputFile=target/cp.txt" "-Dmdep.includeScope=test"
$cp = "target/test-classes;target/classes;" + (Get-Content target/cp.txt)
java "-Dnet.bytebuddy.experimental=true" -cp $cp com.agrilink.DevApplication
```

Then `node scripts/seed-demo-orders.mjs` creates orders in every lifecycle stage (completed, check window, disputed,
on the road, open job, awaiting farmer, awaiting payment, cancelled), withdrawals, an announcement and two people
waiting for verification.

## Tests

```powershell
mvn test
```

* 75 unit tests (JUnit 5 + Mockito): order state machine, order service rules, escrow / refund / dispute
  settlement maths, OTP, handover codes, pricing, listings, phone numbers.
* `EndToEndFlowTest`: boots the whole application against a real **embedded PostgreSQL** (no Docker, no server
  needed), applies the Flyway migrations, lets Hibernate validate the schema, and drives the HTTP API through the full
  trade, disputes, cancellation, verification, listing management, driver rules and admin operations.

## Architecture

`Controller -> Service -> Repository -> PostgreSQL`, one package per feature under `com.agrilink`:

```
auth  user  farmer  buyer  driver  verification  file  region
marketplace (catalogue + listings)  order  delivery  payment  wallet  rating  dispute  notification  admin
security  config  common
```

* **DTOs everywhere**: entities are never serialised. Requests are validated with Jakarta Validation.
* **Events decouple modules**: orders publish `OrderStatusChangedEvent`; delivery jobs are created / cancelled and
  notifications are written by listeners; the order service does not know about either. Listeners run inside the
  transaction, so an order is never `PAID` without its escrow being `HELD`.
* **Concurrency**: listings, orders, deliveries, payments and wallets are loaded with pessimistic locks in a fixed
  order (order, then delivery / payment) and also carry an optimistic `version`. Overselling and double job
  acceptance are prevented in the database, not in application memory.
* **Auditing**: every table has `created_at / updated_at / created_by / updated_by / version`. Admin actions are in
  `admin_audit_logs`; order transitions in `order_status_history`.
* **Time**: stored as UTC `timestamptz`; availability windows use Africa/Addis_Ababa.

### Order lifecycle

```
PENDING -> ACCEPTED -> PAYMENT_PENDING -> PAID -> READY_FOR_PICKUP -> PICKED_UP -> IN_TRANSIT -> DELIVERED -> COMPLETED
   |          |             |               |            |
   +-REJECTED +-CANCELLED   +-> ACCEPTED    +-CANCELLED  +-CANCELLED      (any funded state -> DISPUTED -> COMPLETED | CANCELLED)
   +-EXPIRED  +-EXPIRED       (payment failed: retry)
```

Rules live in one class, `OrderStateMachine`. Timers (`OrderMaintenanceJob`): unanswered orders expire after 2 h,
unpaid orders after 30 min, delivered orders complete automatically after the buyer's 6 h check window. Reserved
stock returns to the listing whenever an order is rejected, cancelled or expires before pickup.

### Money

* Buyer pays **goods + delivery fee + 2% AgriLink fee** (design: 400 kg x ETB 46 -> 18,400 + delivery + 368).
* The payment is **held in escrow** (`HELD`). On completion the farmer receives the goods value, the driver the
  delivery fee, and AgriLink keeps its fee. Cancellation before pickup refunds the buyer in full.
* Disputes freeze the order; an admin settles it: release all, full refund, or a custom split (`PARTIAL`) with the
  unallocated remainder kept as fee.
* Farmers and drivers withdraw their wallet to telebirr / CBE Birr / bank (`/wallet/withdrawals`).

### Handover codes

On `PAID` a delivery job is posted with two 6-digit codes (and QR tokens): the **pickup code** is shown to the
farmer, the **delivery code** to the buyer. The driver never sees either, they must receive them at each end. Wrong
codes are counted; after 5 failures the handover locks until an admin resets it.

### Role-aware responses

Order and delivery views include `allowedActions` (e.g. `ACCEPT`, `PAY`, `CONFIRM_DELIVERY`, `REPORT_PROBLEM`,
`RATE`) so the Android, iOS and web clients render the same buttons without re-implementing workflow rules.
Counterparty phone numbers appear only after payment; exact pickup addresses are hidden on the public job board.

## API conventions

* Base path `/api/v1`. JSON. `Authorization: Bearer <accessToken>` (15 min). Refresh tokens are opaque, rotate on
  every use, and a replayed token revokes its family.
* Lists are paginated: `?page=0&size=20&sort=...` and return
  `{ items, page, size, totalItems, totalPages, hasNext }`.
* Errors always have the same shape; clients switch on `code`, never on `message`:

```json
{ "timestamp": "...", "status": 409, "code": "INSUFFICIENT_STOCK", "message": "Only 100 kg of 'Tomato A' left",
  "path": "/api/v1/orders", "fieldErrors": [ { "field": "items[0].quantity", "message": "..." } ] }
```

* Money is a decimal string/number in ETB with two decimals. Dates are ISO-8601.
* Partial updates use `PATCH`.

### Endpoint overview

| Area | Endpoints |
|------|-----------|
| Auth | `POST /auth/register`, `/auth/otp/request`, `/auth/otp/verify`, `/auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/password/change`, `/auth/password/reset` |
| Account | `GET/PATCH /users/me`; `GET/PATCH /farmers/me`, `/buyers/me`, `/drivers/me`; `PUT /drivers/me/availability`; `POST /drivers/me/location`; `GET /farmers/{id}/public` |
| Verification | `GET /verification`, `POST /verification/documents`, `DELETE /verification/documents/{id}`, `POST /verification/submit` |
| Files | `POST /files` (multipart, `purpose`), `GET /files/{id}` |
| Marketplace | `GET /regions`, `/categories`, `/products`, `/listings` (filters + sort), `/listings/{id}`; farmer: `POST/PATCH/DELETE /listings`, `PUT /listings/{id}/status`, `POST/DELETE /listings/{id}/photos`, `GET /farmers/me/listings` |
| Orders | `POST /orders/quote`, `POST /orders`, `GET /orders`, `GET /orders/{id}`, `/timeline`, `POST /orders/{id}/accept\|reject\|ready\|confirm-delivery\|cancel` |
| Payments | `GET /payments/methods`, `POST /orders/{id}/payments`, `GET /orders/{id}/payments`, `GET /payments/{id}`, `GET /payments/mine`, `POST /payments/webhooks/{provider}` |
| Delivery | `GET /deliveries/available`, `/deliveries/mine`, `/deliveries/{id}`, `/orders/{id}/delivery`, `/deliveries/{id}/events`; driver: `POST /deliveries/{id}/accept\|release\|pickup\|start\|location\|deliver` |
| Wallet | `GET /wallet`, `/wallet/transactions`, `POST /wallet/withdrawals`, `GET /wallet/withdrawals` |
| Ratings | `POST/GET /orders/{id}/ratings`, `GET /users/{id}/ratings` |
| Disputes | `POST /orders/{id}/disputes`, `GET /disputes`, `/disputes/{id}`, `POST /disputes/{id}/evidence` |
| Notifications | `GET /notifications`, `/notifications/unread-count`, `POST /notifications/{id}/read`, `/read-all`, `POST/DELETE /devices` |
| Admin | `/admin/dashboard`, `/reports/summary`, `/users`, `/verifications`, `/categories`, `/products`, `/listings`, `/orders`, `/deliveries`, `/drivers/available`, `/payments`, `/payouts`, `/disputes`, `/notifications`, `/audit-logs`, `/settings` |

Dev only (profile `dev`): `POST /dev/payments/{id}/confirm|fail` simulate the provider callback when
`agrilink.payment.mock.auto-confirm=false`.

## Configuration

Everything is under `agrilink.*` in `application.yml` and overridable by environment variables.

| Variable | Purpose |
|----------|---------|
| `AGRILINK_DB_URL`, `AGRILINK_DB_USERNAME`, `AGRILINK_DB_PASSWORD` | PostgreSQL |
| `AGRILINK_JWT_SECRET` | **required** outside the dev profile; at least 32 bytes |
| `AGRILINK_CORS_ORIGINS` | comma-separated origins (admin web dev server is allowed by default) |
| `AGRILINK_ADMIN_PHONE`, `AGRILINK_ADMIN_PASSWORD` | first admin account, created at start-up if missing |
| `AGRILINK_PAYMENT_WEBHOOK_SECRET` | shared secret of the mock provider webhook |
| `AGRILINK_STORAGE_PATH` | upload directory (local-disk storage) |

Business rules (fees, delivery pricing, time windows, retry limits) are in `agrilink.pricing`, `agrilink.orders`,
`agrilink.otp`, `agrilink.disputes` and are visible read-only at `GET /admin/settings`.

## Extension points

* **Payment provider** (Chapa, telebirr, CBE Birr): implement `payment.PaymentProvider` (charge, refund, payout,
  webhook parsing) as a Spring bean and set `agrilink.payment.default-provider`. Orders, deliveries and disputes do
  not change. A late success on an already-cancelled charge is refunded automatically.
* **SMS** (AfroMessage, Ethio Telecom) and **push** (FCM / APNs): implement `notification.SmsGateway` /
  `PushGateway`; the logging defaults back off. USSD or WhatsApp: add a `NotificationChannel`.
* **File storage** (S3 etc.): implement `file.FileStorage`.
* **Languages**: notifications and SMS are rendered per recipient from `notifications*.properties` (English,
  Amharic, Afaan Oromoo). The Amharic and Oromo texts are drafts that need a native-speaker review.

## Known limitations (Phase 1)

* One farmer per order; multi-pickup routes and route optimisation are not implemented.
* Upload type checks use the declared content type (no magic-byte sniffing or virus scanning).
* Real provider integrations, SMS and push are stubs behind the interfaces above.
* Request quotes / counter offers (RFQ), standing orders and USSD ordering are later phases.
