# Payment Gateway — Practice Project (Milestones M1 + M2)

**Practice only.** No real money, no real card data, no PCI scope, no licenses.
All payments go through mock PSPs with test values (e.g. test card
`4242424242424242`). Raw card/bank values are tokenized immediately and never
stored — only a vault token + last4 reach the database.

Pairs with [PegLedger](../stablecoin-ledger) as a second payments-systems
project. Full plan: `../user/files/payment-gateway-practice-plan.md`.

## What M1 builds

- **Merchant API** — onboarding + API key auth (`X-Api-Key` header)
- **Payment pipeline** — validate → tokenize → fraud hook → route
  (method adapter → PSP adapter) → ledger → status update → notify
- **Framework shape** — `PaymentMethodAdapter` / `PSPAdapter` core interfaces,
  plugin registry, config-selected default PSP. Adding a payment method or PSP
  = one new adapter class, no core changes.
- **Payment methods** — CARD (full flow), ACH + IBP (minimal mock adapters;
  full ACH batch / IBP flows land in M2)
- **Payment state machine** — `PENDING → AUTHORIZED → CAPTURED → SETTLED`,
  plus `FAILED`, `REFUNDED`/`PARTIALLY_REFUNDED`, `VOIDED`; invalid
  transitions (e.g. refund before capture) are rejected with HTTP 409
- **Double-entry ledger** — derived balances + per-payment balance invariant
  (PegLedger-style):
  - AUTH: Dr `PSP_PENDING` / Cr `MERCHANT_PAYABLE`
  - CAPTURE: Dr `PSP_CLEARING` / Cr `PSP_PENDING`
- **Mock token vault** — in-memory; the PSP adapter receives a token, never
  the raw value

## What M2 adds

- **Idempotency** — `Idempotency-Key` header on payment creation. Same key +
  same payload replays the original payment (HTTP 200, no duplicate charge or
  ledger entries); same key + different payload is a 409.
- **Refunds** — `POST /v1/payments/{id}/refund`, full or partial. Partial →
  `PARTIALLY_REFUNDED`, full → `REFUNDED`. Refund only after capture (409
  otherwise); over-refund is a 400. Ledger refund entries keep debits ==
  credits (Dr `MERCHANT_PAYABLE` / Cr `PSP_CLEARING`).
- **Signed webhooks** — HMAC-SHA256 payloads (`t=<epoch>,v1=<hex>`, Stripe
  style), secret from `gateway.webhook-secret` (env `WEBHOOK_SECRET`). First
  attempt is synchronous; failures retry with exponential backoff
  (`gateway.webhook.max-attempts` / `backoff-base-ms`) via a scheduled job, or
  manually with `POST /v1/webhooks/deliveries/{id}/retry`. Transport is the
  M1 logging notifier (swap `WebhookTransport` for real HTTP later).
  Deliveries are listable per merchant: `GET /v1/webhooks/deliveries`.
- **Second PSP + failover + routing** — `mock-psp-2` alongside `mock-psp`.
  Country rules in `gateway.routing` (US: primary `mock-psp`, secondary
  `mock-psp-2`, `primary-percent`, optional `amount-threshold-minor` and
  `secondary-methods`). Primary failure/timeout auto-routes to the secondary;
  per-PSP circuit breaker (OPEN after 3 consecutive failures, 30s cooldown,
  one HALF_OPEN probe). Traffic split is deterministic per payment id
  (hash-based), so replays are stable.
- **ACH flow** — capture stamps `settlementDueAt` (configurable batch delay,
  default 2 days); a scheduled job settles due payments (SETTLE ledger
  entries, status `SETTLED`).
- **IBP flow** — capture settles immediately (SETTLE entries at capture time).
- **Mock FX** — fixed rates (per 1 USD: EUR 0.92, GBP 0.79, INR 83.50,
  JPY 149.00, CAD 1.36). Rate is locked at authorize time; payment + ledger
  entries carry presentment currency, settlement currency (`USD` by default),
  applied rate, and settlement amount.
- **No-PAN invariant** — tests prove no PAN reaches the DB, ledger, API
  responses, webhook payloads, or idempotency hashes.
- **Pipeline hooks** — `PaymentPipeline` wires validation, fraud, and routing
  as extension points; the service calls the pipeline, adapters never pick a
  PSP directly.

## Run locally

Requires Java 21 + Docker (for Postgres).

```bash
docker compose up -d        # starts Postgres on localhost:5432
mvn spring-boot:run         # app on http://localhost:8080
```

Note: the app JVM is pinned to UTC via the spring-boot plugin
(`-Duser.timezone=UTC` in `pom.xml`). Without it, a JVM reporting a zone
like `US/Pacific` makes the Postgres driver fail at connect time with
`FATAL: invalid value for parameter "TimeZone"`.

Tests (H2 in-memory, no Postgres needed):

```bash
mvn test
```

## Endpoints

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| POST | `/v1/merchants` | — | Body `{"name":"..."}`. Returns `merchantId` + `apiKey` **once**. |
| POST | `/v1/payments` | `X-Api-Key` | Creates + authorizes a payment. Optional `Idempotency-Key` header. Optional body `country` (default `US`). |
| GET | `/v1/payments/{id}` | `X-Api-Key` | Payment status + PSP reference + FX fields. |
| POST | `/v1/payments/{id}/capture` | `X-Api-Key` | Full-amount capture (`AUTHORIZED` only). |
| POST | `/v1/payments/{id}/refund` | `X-Api-Key` | Full (empty body) or partial (`{"amountMinor":2500}`). `CAPTURED`/`PARTIALLY_REFUNDED` only. |
| GET | `/v1/webhooks/deliveries` | `X-Api-Key` | This merchant's webhook deliveries (newest first). |
| GET | `/v1/webhooks/deliveries/{id}` | `X-Api-Key` | One delivery (payload + signature + attempts). |
| POST | `/v1/webhooks/deliveries/{id}/retry` | `X-Api-Key` | Manually retry a failed delivery. |

### Example

```bash
# 1. Create a merchant (save the apiKey — it is shown only once)
curl -s -X POST localhost:8080/v1/merchants \
  -H 'Content-Type: application/json' \
  -d '{"name":"Demo Store"}'

# 2. Create + authorize a $50.00 card payment (test card only).
#    The Idempotency-Key makes a retry safe: same key + same body replays
#    the original payment (200) instead of charging twice.
curl -s -X POST localhost:8080/v1/payments \
  -H 'Content-Type: application/json' -H 'X-Api-Key: sk_test_...' \
  -H 'Idempotency-Key: order-1001' \
  -d '{"method":"CARD","amountMinor":5000,"currency":"USD","country":"US","paymentDetails":"4242424242424242"}'

# 3. Check status (note fxRate / settlementAmountMinor on EUR payments)
curl -s localhost:8080/v1/payments/<payment-id> -H 'X-Api-Key: sk_test_...'

# 4. Capture
curl -s -X POST localhost:8080/v1/payments/<payment-id>/capture -H 'X-Api-Key: sk_test_...'

# 5. Partial refund of $20.00 (empty body refunds the full remaining balance)
curl -s -X POST localhost:8080/v1/payments/<payment-id>/refund \
  -H 'Content-Type: application/json' -H 'X-Api-Key: sk_test_...' \
  -d '{"amountMinor":2000}'

# 6. EUR payment — FX locked at authorize time (EUR 50.00 -> USD 54.35)
curl -s -X POST localhost:8080/v1/payments \
  -H 'Content-Type: application/json' -H 'X-Api-Key: sk_test_...' \
  -d '{"method":"CARD","amountMinor":5000,"currency":"EUR","paymentDetails":"4242424242424242"}'

# 7. ACH payment — capture stays CAPTURED until the batch job settles it;
#    IBP settles immediately at capture
curl -s -X POST localhost:8080/v1/payments \
  -H 'Content-Type: application/json' -H 'X-Api-Key: sk_test_...' \
  -d '{"method":"ACH","amountMinor":5000,"currency":"USD","paymentDetails":"mock-bank-account-123"}'

# 8. Webhook deliveries for this merchant (HMAC-signed payloads)
curl -s localhost:8080/v1/webhooks/deliveries -H 'X-Api-Key: sk_test_...'
curl -s -X POST localhost:8080/v1/webhooks/deliveries/<delivery-id>/retry -H 'X-Api-Key: sk_test_...'
```

Amounts are in minor units (cents). `paymentDetails` is the test-only
sensitive value: test card number for CARD, mock bank account id for ACH,
mock bank reference for IBP.

### Failover drill (manual)

1. Start the app, create a merchant + payment — note `pspReference` starts
   with `mock_psp_` (primary).
2. Kill the primary. There is no admin endpoint; in code or a test, call
   `MockPspAdapter.setSimulateFailure(true)` (mock-only kill switch).
3. Create another payment — it still authorizes, now with a reference
   starting `mock2_psp_` (secondary). After 3 consecutive primary failures
   the primary's circuit breaker opens and the router skips it outright.
4. Restore with `setSimulateFailure(false)` — traffic returns to the primary.
   The automated version of this drill is `FailoverDrillTest`.

## How to add a new payment method

1. Implement `PaymentMethodAdapter` (see `CardPaymentAdapter`).
2. Annotate it with `@Component` — the `AdapterRegistry` picks it up
   automatically, keyed by `PaymentMethod`.
3. Add the enum value + a test. Core code does not change.

How to add a new PSP: implement `PSPAdapter` with a unique `name()`,
annotate `@Component`, and set `gateway.default-psp` (or an M2 routing rule)
to its name.

## What M3 adds (not in M2)

AWS deploy is M3, dashboard + reconciliation is M4.

## Config reference (`gateway.*` in `application.yml`)

| Key | Default | Notes |
|-----|---------|-------|
| `default-psp` | `mock-psp` | PSP used when no routing rule matches |
| `settlement-currency` | `USD` | FX target; rate locked at authorize time |
| `webhook-secret` | `whsec_test_local_only` | HMAC secret (`WEBHOOK_SECRET` env) |
| `webhook.max-attempts` | `3` | Delivery attempts before giving up |
| `webhook.backoff-base-ms` | `1000` | Backoff = base × 2^(attempt-1) |
| `webhook-retry-delay-ms` | `30000` | Scheduled retry job interval |
| `settlement-job-delay-ms` | `30000` | Scheduled ACH settlement job interval |
| `ach.settlement-delay-ms` | `172800000` (2 days) | Mock ACH batch delay after capture |
| `routing.<CC>.primary` / `.secondary` | — | Country → PSP mapping |
| `routing.<CC>.primary-percent` | `100` | % of traffic to the primary |
| `routing.<CC>.amount-threshold-minor` | — | Amounts ≥ this go to the secondary |
| `routing.<CC>.secondary-methods` | `[]` | Methods that always use the secondary |
