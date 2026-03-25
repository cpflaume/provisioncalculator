# Provisionsberechnungs-Microservice — Plan

## Context

Greenfield microservice for multi-tenant commission calculation. Customers sit in a tree; purchases trigger commission payouts up the tree based on depth (e.g. depth-1 = 1%, depth-2 = 3%, depth-3 = 5%). Three core API groups: configure, submit purchases, calculate. Must be fast on trees with 5000+ nodes. Future special rules (e.g. small-tenant bonus) must be pluggable without modifying core logic.

**Stack:** Kotlin · Spring Boot 3.x · PostgreSQL · Flyway · Spring Data JPA
**Multi-tenancy:** `tenant_id` discriminator column on every table + `{tenantId}` in all URL paths
**Repository:** `provisioncalculator/`
**Settlement lifecycle:** OPEN → CALCULATED → APPROVED / REJECTED → OPEN

---

## Package Structure

```
src/main/kotlin/com/provisions/calculator/
├── ProvisionCalculatorApplication.kt
├── model/
│   ├── Settlement.kt               # + SettlementStatus enum (OPEN/CALCULATED/APPROVED/REJECTED)
│   ├── CommissionSettings.kt
│   ├── CommissionRate.kt           # depth -> ratePercent
│   ├── TreeNode.kt                 # adjacency list: externalId + parentId FK (nullable)
│   ├── Purchase.kt
│   ├── Calculation.kt              # one record per calculation run; holds inputHash
│   └── CommissionResult.kt         # audit-trail row: one per (recipient, sourcePurchase, rule)
├── repository/
│   ├── SettlementRepository.kt
│   ├── CommissionSettingsRepository.kt
│   ├── TreeNodeRepository.kt
│   ├── PurchaseRepository.kt
│   ├── CalculationRepository.kt
│   └── CommissionResultRepository.kt
├── api/
│   ├── SettlementController.kt     # settlements CRUD + config + approve/reject
│   ├── PurchaseController.kt
│   ├── CalculationController.kt
│   └── request/
│       ├── CreateSettlementRequest.kt
│       ├── ConfigureSettingsRequest.kt   # contains nested CommissionRateRequest data class
│       ├── TreeNodeRequest.kt
│       └── SubmitPurchasesRequest.kt
├── engine/
│   ├── CommissionRule.kt               # strategy interface → returns List<CommissionLineItem>
│   ├── CommissionLineItem.kt           # recipient, sourcePurchaseId, amount, depth, ruleId
│   ├── CalculationContext.kt           # immutable snapshot; also contains TreeNodeMemento
│   ├── CommissionRuleEngine.kt         # collects all @Component rules, sorts by order, runs all
│   └── rules/
│       └── DepthBasedCommissionRule.kt # default rule; order=100
├── service/
│   ├── SettlementService.kt            # CRUD + configure + approve/reject
│   ├── TreeService.kt                  # DB load → HashMap (private) + validation
│   ├── PurchaseService.kt
│   └── CalculationService.kt           # full pipeline + idempotency (InputHasher logic inline)
└── GlobalExceptionHandler.kt           # @RestControllerAdvice using ResponseStatusException
```

---

## Domain / Database Model

### Tables & Key Columns

| Table | Key columns |
|---|---|
| `settlement` | `id`, `tenant_id`, `name`, `start_date`, `end_date`, `status` (OPEN/CALCULATED/APPROVED/REJECTED) |
| `commission_settings` | `id`, `tenant_id`, `settlement_id` · UNIQUE(`tenant_id`, `settlement_id`) |
| `commission_rate` | `id`, `tenant_id`, `settings_id`, `depth`, `rate_percent` · UNIQUE(`settings_id`, `depth`) |
| `tree_node` | `id`, `tenant_id`, `settlement_id`, `external_id`, `parent_node_id` (nullable self-ref FK) · UNIQUE(`tenant_id`, `settlement_id`, `external_id`) |
| `purchase` | `id`, `tenant_id`, `settlement_id`, `buyer_external_id`, `amount`, `purchased_at` |
| `calculation` | `id` (UUID PK), `tenant_id`, `settlement_id`, `input_hash`, `calculated_at` · UNIQUE(`tenant_id`, `settlement_id`, `input_hash`) |
| `commission_result` | `id`, `tenant_id`, `settlement_id`, `calculation_id` (FK → calculation), `recipient_external_id`, `source_purchase_id` (nullable FK → purchase), `amount`, `depth` (nullable), `rule_id` |

All money/rate fields: `NUMERIC(15,4)` / `NUMERIC(8,4)`. Rounding: `BigDecimal` HALF_UP.

### Key Design Notes

- **`calculation` table** is the idempotency anchor. Each run produces a stable `input_hash`; if a row already exists for `(tenant_id, settlement_id, input_hash)`, the existing result is returned immediately without re-computing.
- **`commission_result`** stores one row per `(recipient, sourcePurchase, rule)` — full audit trail. `source_purchase_id` is `NULL` for aggregate rules not tied to a single purchase. `depth` is `NULL` for non-depth rules.

### Critical Indexes

```sql
CREATE INDEX idx_tree_node_tenant_settlement   ON tree_node (tenant_id, settlement_id);
CREATE INDEX idx_tree_node_parent              ON tree_node (parent_node_id);
CREATE INDEX idx_purchase_tenant_settlement    ON purchase  (tenant_id, settlement_id);
CREATE INDEX idx_calculation_tenant_settlement ON calculation (tenant_id, settlement_id);
CREATE INDEX idx_result_calculation            ON commission_result (calculation_id);
CREATE INDEX idx_result_recipient              ON commission_result (calculation_id, recipient_external_id);
CREATE INDEX idx_result_source_purchase        ON commission_result (source_purchase_id);
```

### Flyway Migrations

```
src/main/resources/db/migration/
├── V1__create_settlement.sql
├── V2__create_commission_settings_and_rates.sql
├── V3__create_tree_node.sql
├── V4__create_purchase.sql
├── V5__create_calculation.sql
├── V6__create_commission_result.sql
└── V7__add_indexes.sql
```

---

## REST API

All URLs are rooted under `/api/v1/tenants/{tenantId}`.

### Settlements

```
POST   /api/v1/tenants/{tenantId}/settlements
         Body: { name, startDate, endDate }
         → 201  { id, tenantId, name, startDate, endDate, status, createdAt }

GET    /api/v1/tenants/{tenantId}/settlements/{settlementId}
GET    /api/v1/tenants/{tenantId}/settlements?status=

PUT    /api/v1/tenants/{tenantId}/settlements/{settlementId}/config
         Body: {
           rates: [{ depth: 1, ratePercent: 1.0 }, { depth: 2, ratePercent: 3.0 }, ...],
           tree:  [{ externalId: "A", parentExternalId: null }, { externalId: "B", parentExternalId: "A" }, ...]
         }
         → 200  { settlementId, rates, nodeCount, updatedAt }

GET    /api/v1/tenants/{tenantId}/settlements/{settlementId}/config
```

> PUT config replaces the entire tree atomically and resets status to OPEN if previously CALCULATED.

### Approval

```
POST   /api/v1/tenants/{tenantId}/settlements/{settlementId}/approve
         Body: {}
         → 200  { settlementId, status: "APPROVED" }

POST   /api/v1/tenants/{tenantId}/settlements/{settlementId}/reject
         Body: {}
         → 200  { settlementId, status: "REJECTED" }
```

> Rejection automatically resets status to OPEN.

### Purchases

```
POST   /api/v1/tenants/{tenantId}/settlements/{settlementId}/purchases
         Body: { purchases: [{ buyerExternalId, amount, purchasedAt }] }
         → 202  { settlementId, accepted, submittedAt }

GET    /api/v1/tenants/{tenantId}/settlements/{settlementId}/purchases?page=&size=
```

### Calculation

```
POST   /api/v1/tenants/{tenantId}/settlements/{settlementId}/calculate
         Body: {}
         → 200  { calculationId, settlementId, calculatedAt, cached,
                  results: [{ recipientExternalId, totalCommission }] }

GET    /api/v1/tenants/{tenantId}/settlements/{settlementId}/calculation
GET    /api/v1/tenants/{tenantId}/settlements/{settlementId}/calculation/recipients/{externalId}
GET    /api/v1/tenants/{tenantId}/settlements/{settlementId}/calculation/audit
```

---

## Commission Rule Engine

### CommissionLineItem

```kotlin
data class CommissionLineItem(
    val recipientExternalId: String,
    val sourcePurchaseId: Long?,   // null for aggregate rules
    val amount: BigDecimal,
    val depth: Int?,               // null for non-depth rules
    val ruleId: String
)
```

### CommissionRule interface

```kotlin
interface CommissionRule {
    val ruleId: String
    val order: Int get() = 100
    fun isApplicable(context: CalculationContext): Boolean = true
    fun calculate(context: CalculationContext): List<CommissionLineItem>
}
```

### CalculationContext

```kotlin
// TreeNodeMemento lives here as a nested or same-file data class
data class TreeNodeMemento(val externalId: String, val parentExternalId: String?, val children: List<String>)

data class CalculationContext(
    val tenantId: String,
    val settlement: Settlement,
    val ratesByDepth: Map<Int, BigDecimal>,
    val treeMap: Map<String, TreeNodeMemento>,
    val purchases: List<Purchase>,
    val totalRevenue: BigDecimal,
    val nodeCount: Int
)
```

### Engine

Spring injects all `@Component` beans implementing `CommissionRule` as `List<CommissionRule>`, sorted by `order`. Each rule returns a list of line items; the engine concatenates them all. No aggregation at write time — raw line items are persisted for the audit trail.

### Default: `DepthBasedCommissionRule` (order=100)

For each purchase, walk up the `treeMap` HashMap from buyer to root. For each ancestor at depth N, emit a `CommissionLineItem` with `amount = purchase.amount × ratesByDepth[N]`. Stops at the deepest configured depth. Pure in-memory, O(purchases × maxDepth).

**Adding a new rule = add one `@Component` class implementing `CommissionRule`. Nothing else changes.**

---

## Idempotency Design

Calculation is **fully idempotent**: the same inputs always produce the same result.

### Mechanism (inline in `CalculationService`)

1. Compute `inputHash = SHA-256(tenantId + settlementId + sortedDepthRates + sortedPurchaseIds)`
2. Query `calculation` table by `(tenant_id, settlement_id, input_hash)`
3. **Found** → return stored results immediately, `cached: true`
4. **Not found** → run engine, bulk-insert `CommissionResult` rows, insert `Calculation` row, `cached: false`

### Invariants

- Same settings + same purchases → always same `calculationId` and same result rows.
- Any change to config or purchases produces a new hash → new calculation run; old results preserved.

### Settlement status transitions

```
OPEN ──(PUT config / POST purchases)───► OPEN
OPEN ──(POST calculate)────────────────► CALCULATED
CALCULATED ──(POST calculate, same)────► CALCULATED   (cached, no recompute)
CALCULATED ──(PUT config / purchases)──► OPEN         (invalidates calculation)
CALCULATED ──(POST approve)────────────► APPROVED
CALCULATED ──(POST reject)─────────────► REJECTED → OPEN  (auto-reset)
APPROVED ──(all writes → 409 Conflict)
```

---

## Key Service Signatures

```kotlin
// SettlementService
fun create(tenantId: String, request: CreateSettlementRequest): SettlementResponse
fun findById(tenantId: String, settlementId: Long): SettlementResponse
fun configure(tenantId: String, settlementId: Long, request: ConfigureSettingsRequest): SettlementResponse
fun approve(tenantId: String, settlementId: Long): SettlementResponse   // CALCULATED → APPROVED
fun reject(tenantId: String, settlementId: Long): SettlementResponse    // CALCULATED → REJECTED → OPEN

// TreeService  (tree building is a private fun inside this class)
fun loadTreeIntoMemory(tenantId: String, settlementId: Long): Map<String, TreeNodeMemento>
fun validate(nodes: List<TreeNodeRequest>)   // cycle detection, single root, valid refs

// PurchaseService
fun submitBatch(tenantId: String, settlementId: Long, request: SubmitPurchasesRequest): PurchaseResponse

// CalculationService  (input hashing is a private fun inside this class)
fun calculate(tenantId: String, settlementId: Long): CalculationResponse
fun getResults(tenantId: String, settlementId: Long): CalculationResponse
fun getAuditTrail(tenantId: String, settlementId: Long): List<CommissionResultResponse>
fun getResultForRecipient(tenantId: String, settlementId: Long, recipientExternalId: String): CommissionResultResponse
```

---

## Performance Strategy (5000+ nodes)

| Stage | Approach | Target |
|---|---|---|
| Idempotency check | Single indexed SELECT on `calculation` | < 5 ms |
| Tree load | Single `SELECT *` + O(n) HashMap | < 200 ms |
| Commission traversal | In-memory HashMap walk, O(purchases × maxDepth) | < 500 ms (10k purchases) |
| Result persist | `saveAll` + `hibernate.jdbc.batch_size=500` | < 500 ms |
| **Total (cold)** | | **< 2 s** |
| **Total (cached)** | Hash lookup only | **< 10 ms** |

---

## Testing Strategy

| Layer | Coverage | Tools |
|---|---|---|
| Unit | `DepthBasedCommissionRule`, `CommissionRuleEngine`, `TreeService`, `CalculationService` | JUnit 5 + MockK |
| Integration | All controllers, multi-tenant isolation, idempotency | `@SpringBootTest` + Testcontainers PostgreSQL |
| Idempotency | Call `calculate` twice → same `calculationId`, no duplicate rows | Integration test |
| Approval flow | Approve happy path; reject → OPEN → recalculate → approve | Integration test |
| Frozen state | Writes on APPROVED → 409 | Integration test |
| Migration | All Flyway scripts apply clean | Testcontainers PostgreSQL |

---

## README Diagrams (to be added to README.md)

1. **Commission Tree Example** — Mermaid graph: tree nodes + purchase → ancestor payouts with %
2. **API Flow** — Mermaid sequence: Configure → Submit Purchases → Calculate (cache branch)
3. **Settlement Status FSM** — Mermaid state diagram: OPEN → CALCULATED → APPROVED / REJECTED
4. **ER Diagram** — Mermaid `erDiagram`: all 7 tables

All in Mermaid (renders natively on GitHub).

---

## Architectural Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Multi-tenancy | `tenant_id` column + URL path segment | DB-level isolation + explicit scope on every call |
| Tree storage | Adjacency list | Simple; fast full-replace; single-query bulk load |
| Tree traversal | In-memory HashMap | No N+1; no recursive CTE; 5000 nodes ≈ 1 MB |
| Rule return type | `List<CommissionLineItem>` | Full audit trail per purchase per rule |
| Idempotency | SHA-256 input hash, inline in `CalculationService` | Simple, deterministic, DB-enforced via UNIQUE |
| Rule discovery | Spring `List<CommissionRule>` injection | Add `@Component` = done. No registration boilerplate |
| Tree replace | DELETE all + bulk INSERT in one `@Transactional` | Matches "whole new tree" requirement |
| Rounding | `BigDecimal` HALF_UP, 4 decimal places | Financial precision |
| Result history | New `Calculation` + `CommissionResult` rows per run | History preserved; old results never overwritten |
| Approval/rejection | Status field on `settlement` only | No extra table needed — status is the only thing that changes |
| Rejection | REJECTED auto-resets to OPEN | No manual step required to resume corrections |
