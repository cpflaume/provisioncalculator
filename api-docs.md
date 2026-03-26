# Provision Calculator API

A multi-tenant commission calculation service. Calculate depth-based commissions across referral trees for any settlement period.

**Base URL:** `http://localhost:8080/api/v1/tenants/{tenantId}`

Every request is scoped to a **tenant**. Tenants are isolated — data from one tenant is never visible to another.

---

## Quick Start — Complete Example

This walkthrough shows the full lifecycle using `curl`. Replace `localhost:8080` with your server address.

### The Scenario

A company has a referral tree of 4 people:

```
Alice (root)
├── Bob
│   └── Diana
└── Carol
```

Diana makes a purchase of 1,000.00. The company pays commissions up the tree:
- **Bob** (1 level up) gets **10%** = 100.00
- **Alice** (2 levels up) gets **5%** = 50.00
- Carol gets nothing (not in Diana's upline)

Let's set this up step by step.

### Step 1 — Create a Settlement

A settlement is a billing period (e.g., a month). All purchases and commissions belong to a settlement.

```bash
curl -X POST http://localhost:8080/api/v1/tenants/acme/settlements \
  -H "Content-Type: application/json" \
  -d '{
    "name": "March 2026"
  }'
```

**Response** `201 Created`:
```json
{
  "id": 1,
  "tenantId": "acme",
  "name": "March 2026",
  "status": "OPEN",
  "createdAt": "2026-03-26T10:00:00"
}
```

> Save the `id` — you'll need it for all subsequent requests.

### Step 2 — Configure the Referral Tree and Commission Rates

Upload the tree structure and commission rates in a single request.

```bash
curl -X PUT http://localhost:8080/api/v1/tenants/acme/settlements/1/config \
  -H "Content-Type: application/json" \
  -d '{
    "rates": [
      { "depth": 1, "ratePercent": 10.0 },
      { "depth": 2, "ratePercent": 5.0 }
    ],
    "tree": [
      { "customerId": "alice", "parentCustomerId": null },
      { "customerId": "bob",   "parentCustomerId": "alice" },
      { "customerId": "carol", "parentCustomerId": "alice" },
      { "customerId": "diana", "parentCustomerId": "bob" }
    ]
  }'
```

**Response** `200 OK`:
```json
{
  "settlementId": 1,
  "rates": [
    { "depth": 1, "ratePercent": 10.0 },
    { "depth": 2, "ratePercent": 5.0 }
  ],
  "nodeCount": 4,
  "updatedAt": "2026-03-26T10:01:00"
}
```

**How rates work:**
- `depth: 1` = direct upline (parent)
- `depth: 2` = grandparent
- `depth: 3` = great-grandparent, etc.

The rate is a percentage of the purchase amount.

### Step 3 — Submit Purchases

Submit one or more purchases made by customers in the tree.

```bash
curl -X POST http://localhost:8080/api/v1/tenants/acme/settlements/1/purchases \
  -H "Content-Type: application/json" \
  -d '{
    "purchases": [
      {
        "buyerCustomerId": "diana",
        "amount": 1000.00,
        "purchasedAt": "2026-03-15T14:30:00"
      },
      {
        "buyerCustomerId": "carol",
        "amount": 500.00,
        "purchasedAt": "2026-03-16T09:00:00"
      }
    ]
  }'
```

**Response** `202 Accepted`:
```json
{
  "settlementId": 1,
  "accepted": 2,
  "submittedAt": "2026-03-26T10:02:00"
}
```

You can submit purchases in multiple batches — they accumulate.

### Step 4 — Calculate Commissions

Trigger the commission calculation.

```bash
curl -X POST http://localhost:8080/api/v1/tenants/acme/settlements/1/calculate
```

**Response** `200 OK`:
```json
{
  "calculationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "settlementId": 1,
  "calculatedAt": "2026-03-26T10:03:00",
  "fromCache": false,
  "results": [
    { "customerId": "alice", "totalCommission": 100.0000 },
    { "customerId": "bob",   "totalCommission": 100.0000 }
  ]
}
```

**How these numbers break down:**

| Purchase | Buyer | Depth 1 (10%) | Depth 2 (5%) |
|----------|-------|---------------|--------------|
| 1,000.00 | Diana | Bob: 100.00 | Alice: 50.00 |
| 500.00 | Carol | Alice: 50.00 | — |

**Totals:** Alice = 50.00 + 50.00 = **100.00**, Bob = **100.00**

> Calling calculate again with the same data returns the cached result (`"fromCache": true`). The cache is invalidated when purchases, rates, or tree structure change.

### Step 5 — View Detailed Results

**Per recipient:**

```bash
curl http://localhost:8080/api/v1/tenants/acme/settlements/1/calculation/recipients/alice
```

```json
{
  "customerId": "alice",
  "totalCommission": 100.0000,
  "details": [
    { "sourcePurchaseId": 1, "amount": 50.0000, "depth": 2, "ruleId": "DEPTH_BASED" },
    { "sourcePurchaseId": 2, "amount": 50.0000, "depth": 1, "ruleId": "DEPTH_BASED" }
  ]
}
```

**Full audit trail:**

```bash
curl http://localhost:8080/api/v1/tenants/acme/settlements/1/calculation/audit
```

```json
[
  { "recipientCustomerId": "bob",   "sourcePurchaseId": 1, "amount": 100.0000, "depth": 1, "ruleId": "DEPTH_BASED" },
  { "recipientCustomerId": "alice", "sourcePurchaseId": 1, "amount": 50.0000,  "depth": 2, "ruleId": "DEPTH_BASED" },
  { "recipientCustomerId": "alice", "sourcePurchaseId": 2, "amount": 50.0000,  "depth": 1, "ruleId": "DEPTH_BASED" }
]
```

### Step 6 — Approve or Reject

Once you're satisfied with the results, approve the settlement. This locks it — no more changes allowed.

```bash
curl -X POST http://localhost:8080/api/v1/tenants/acme/settlements/1/approve
```

```json
{ "settlementId": 1, "status": "APPROVED" }
```

If the results don't look right, reject instead. This resets the status to OPEN so you can modify purchases/config and recalculate.

```bash
curl -X POST http://localhost:8080/api/v1/tenants/acme/settlements/1/reject
```

```json
{ "settlementId": 1, "status": "OPEN" }
```

---

## Settlement Lifecycle

```
                  configure / submit purchases
                 ┌──────────────────────────────┐
                 │                              │
                 ▼                              │
    ┌────────┐  calculate  ┌────────────┐  reject  │
    │  OPEN  │ ─────────── │ CALCULATED │ ─────────┘
    └────────┘             └────────────┘
                                │
                             approve
                                │
                                ▼
                          ┌──────────┐
                          │ APPROVED │  (locked, read-only)
                          └──────────┘
```

- **OPEN** — accepting purchases and configuration changes
- **CALCULATED** — commissions computed, awaiting approval
- **APPROVED** — finalized, all writes blocked

Submitting new purchases or changing config on a CALCULATED settlement automatically resets it to OPEN.

---

## API Reference

All endpoints are under `/api/v1/tenants/{tenantId}`.

### Settlements

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/settlements` | Create a new settlement |
| `GET` | `/settlements` | List all settlements (optional `?status=OPEN`) |
| `GET` | `/settlements/{id}` | Get settlement by ID |
| `PUT` | `/settlements/{id}/config` | Set commission rates and referral tree |
| `GET` | `/settlements/{id}/config` | Get current config (rates + tree) |
| `POST` | `/settlements/{id}/approve` | Approve (CALCULATED -> APPROVED) |
| `POST` | `/settlements/{id}/reject` | Reject (CALCULATED -> OPEN) |

### Purchases

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/settlements/{id}/purchases` | Submit a batch of purchases |
| `GET` | `/settlements/{id}/purchases` | List purchases (paginated: `?page=0&size=20`) |

### Calculation

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/settlements/{id}/calculate` | Trigger commission calculation |
| `GET` | `/settlements/{id}/calculation` | Get aggregated results |
| `GET` | `/settlements/{id}/calculation/recipients/{customerId}` | Get results for one recipient |
| `GET` | `/settlements/{id}/calculation/audit` | Get full audit trail |

---

## Request & Response Details

### Create Settlement

```
POST /api/v1/tenants/{tenantId}/settlements
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `name` | string | yes | Not blank, max 255 chars |

### Configure Settings

```
PUT /api/v1/tenants/{tenantId}/settlements/{id}/config
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `rates[].depth` | integer | yes | Must be positive |
| `rates[].ratePercent` | decimal | yes | Must be >= 0 |
| `tree[].customerId` | string | yes | Not blank |
| `tree[].parentCustomerId` | string | no | null for root node |

**Tree rules:**
- Exactly one root node (parentCustomerId = null)
- No duplicate customerIds
- No orphan nodes (every parentCustomerId must exist)
- No cycles

### Submit Purchases

```
POST /api/v1/tenants/{tenantId}/settlements/{id}/purchases
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `purchases[].buyerCustomerId` | string | yes | Not blank |
| `purchases[].amount` | decimal | yes | Must be positive |
| `purchases[].purchasedAt` | datetime | yes | ISO 8601 format |

---

## Multi-Tenancy

Every URL contains a `{tenantId}` path segment. This provides complete data isolation:

```bash
# Tenant "acme" — only sees its own data
curl http://localhost:8080/api/v1/tenants/acme/settlements

# Tenant "globex" — completely separate
curl http://localhost:8080/api/v1/tenants/globex/settlements
```

No authentication is needed to use a tenant — just use any string as the tenant ID. Each tenant has its own settlements, trees, purchases, and calculations.

---

## Error Handling

Errors return a consistent JSON format:

```json
{
  "status": 404,
  "message": "Settlement 99 not found"
}
```

| Status | Meaning |
|--------|---------|
| `400` | Validation error (bad input, empty tree, missing rates) |
| `404` | Settlement or calculation not found |
| `409` | State conflict (e.g., trying to approve an OPEN settlement, or modifying an APPROVED one) |

---

## Idempotency

The calculation endpoint is idempotent. Calling it multiple times with the same input data returns the same result without recomputing:

```bash
# First call — computes fresh
curl -X POST .../calculate    # → "fromCache": false

# Second call — returns cached
curl -X POST .../calculate    # → "fromCache": true, same calculationId
```

The cache key is a SHA-256 hash of: tenant, settlement, rates, purchase IDs, tree structure, and active rule set. Changing any of these invalidates the cache.

---

## Financial Precision

All monetary amounts use 4 decimal places with HALF_UP rounding:

```
Purchase: 333.33
Rate: 7.5%
Commission: 333.33 × 7.5 / 100 = 24.9998 (rounded to 4 decimal places)
```

This ensures cent-accurate results even with complex multi-level calculations.
