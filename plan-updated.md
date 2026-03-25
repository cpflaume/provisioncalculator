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
├── config/
│   ├── JpaConfig.kt
│   └── WebConfig.kt                    # OpenAPI / Swagger
├── domain/
│   ├── model/
│   │   ├── Settlement.kt               # + SettlementStatus enum (OPEN/LOCKED/CALCULATED/APPROVED/REJECTED)
│   │   ├── CommissionSettings.kt
│   │   ├── CommissionRate.kt           # depth -> ratePercent
│   │   ├── TreeNode.kt                 # adjacency list: externalId + parentId FK (nullable)
│   │   ├── Purchase.kt
│   │   ├── Calculation.kt              # one record per calculation run; holds inputHash
│   │   └── CommissionResult.kt         # audit-trail row: one per (recipient, sourcePurchase, rule)
│   └── repository/
│       ├── SettlementRepository.kt
│       ├── CommissionSettingsRepository.kt
│       ├── TreeNodeRepository.kt
│       ├── PurchaseRepository.kt
│       ├── CalculationRepository.kt
│       └── CommissionResultRepository.kt
├── api/
│   ├── controller/
│   │   ├── SettlementController.kt     # includes approve/reject endpoints
│   │   ├── SettingsController.kt
│   │   ├── PurchaseController.kt
│   │   └── CalculationController.kt
│   ├── request/
│   │   ├── CreateSettlementRequest.kt
│   │   ├── ConfigureSettingsRequest.kt
│   │   ├── CommissionRateRequest.kt
│   │   ├── TreeNodeRequest.kt
│   │   └── SubmitPurchasesRequest.kt
│   └── response/
│       ├── SettlementResponse.kt
│       ├── SettingsResponse.kt
│       ├── PurchaseResponse.kt
│       ├── CalculationResponse.kt
│       └── CommissionResultResponse.kt
├── engine/
│   ├── CommissionRule.kt               # strategy interface → returns List<CommissionLineItem>
│   ├── CommissionLineItem.kt           # audit-trail unit: recipient, sourcePurchaseId, amount, depth, ruleId
│   ├── CalculationContext.kt           # immutable snapshot passed to every rule
│   ├── TreeNodeMemento.kt              # lightweight in-memory node
│   ├── CommissionRuleEngine.kt         # auto-discovers all @Component rules, sorts by order, runs all
│   └── rules/
│       ├── DepthBasedCommissionRule.kt # default; order=100
│       └── SmallTenantBonusRule.kt     # example pluggable rule; @ConditionalOnProperty
├── service/
│   ├── SettlementService.kt            # includes approve/reject logic
│   ├── SettingsService.kt
│   ├── TreeService.kt                  # DB load → HashMap + tree validation
│   ├── PurchaseService.kt
│   └── CalculationService.kt           # orchestrates full pipeline + idempotency check
├── exception/
│   ├── ProvisionException.kt           # sealed exception hierarchy
│   └── GlobalExceptionHandler.kt
└── util/
    ├── TreeBuilder.kt                  # flat list → HashMap<externalId, TreeNodeMemento>
    └── InputHasher.kt                  # stable SHA-256 hash of (settingsFingerprint + sortedPurchaseIds)
```

---

## Domain / Database Model

### Tables & Key Columns

| Table | Key columns |
|---|---|
| `settlement` | `id`, `tenant_id`, `name`, `start_date`, `end_date`, `status` (OPEN/LOCKED/CALCULATED/APPROVED/REJECTED) |
| `commission_settings` | `id`, `tenant_id`, `settlement_id` · UNIQUE(`tenant_id`, `settlement_id`) |
| `commission_rate` | `id`, `tenant_id`, `settings_id`, `depth`, `rate_percent` · UNIQUE(`settings_id`, `depth`) |
| `tree_node` | `id`, `tenant_id`, `settlement_id`, `external_id`, `parent_node_id` (nullable self-ref FK) · UNIQUE(`tenant_id`, `settlement_id`, `external_id`) |
| `purchase` | `id`, `tenant_id`, `settlement_id`, `buyer_external_id`, `amount`, `purchased_at` |
| `calculation` | `id` (UUID PK), `tenant_id`, `settlement_id`, `input_hash`, `calculated_at` · UNIQUE(`tenant_id`, `settlement_id`, `input_hash`) |
| `commission_result` | `id`, `tenant_id`, `settlement_id`, `calculation_id` (FK → calculation), `recipient_external_id`, `source_purchase_id` (nullable FK → purchase), `amount`, `depth` (nullable), `rule_id` |

All money/rate fields: `NUMERIC(15,4)` / `NUMERIC(8,4)`. Rounding: `BigDecimal` HALF_UP.

### Key Design Notes

- **`calculation` table** is the idempotency anchor. Each run produces a stable `input_hash`; if a row already exists for `(tenant_id, settlement_id, input_hash)`, the existing `calculation_id` is returned immediately without re-computing.
- **`commission_result`** stores one row per `(recipient, sourcePurchase, rule)` — full audit trail. `source_purchase_id` is `NULL` for aggregate rules (e.g. SmallTenantBonusRule) that are not tied to a single purchase. `depth` is `NULL` for non-depth rules.

### Critical Indexes

```sql
CREATE INDEX idx_tree_node_tenant_settlement    ON tree_node (tenant_id, settlement_id);
CREATE INDEX idx_tree_node_parent               ON tree_node (parent_node_id);
CREATE INDEX idx_purchase_tenant_settlement     ON purchase  (tenant_id, settlement_id);
CREATE INDEX idx_calculation_tenant_settlement  ON calculation (tenant_id, settlement_id);
CREATE INDEX idx_result_calculation             ON commission_result (calculation_id);
CREATE INDEX idx_result_recipient               ON commission_result (calculation_id, recipient_external_id);
CREATE INDEX idx_result_source_purchase         ON commission_result (source_purchase_id);
```

### Flyway Migrations

```
src/main/resources/db/migration/
├── V1__create_settlement.sql
├── V2__create_commission_settings.sql
├── V3__create_commission_rate.sql
├── V4__create_tree_node.sql
├── V5__create_purchase.sql
├── V6__create_calculation.sql
├── V7__create_commission_result.sql
├── V8__add_indexes.sql
└── V9__add_constraints_checks.sql
```

---

## REST API

All URLs are rooted under `/api/v1/tenants/{tenantId}`. The `{tenantId}` path segment is the multi-tenancy discriminator and is validated against the authenticated caller on every request.

### Settlements

```
POST   /api/v1/tenants/{tenantId}/settlements
         Body: { name, startDate, endDate }
         → 201  { id, tenantId, name, startDate, endDate, status, createdAt }

GET    /api/v1/tenants/{tenantId}/settlements/{settlementId}
GET    /api/v1/tenants/{tenantId}/settlements?status=
```

### Settings — configure rates + tree in one atomic call

```
PUT    /api/v1/tenants/{tenantId}/settlements/{settlementId}/config
         Body: {
           rates: [{ depth: 1, ratePercent: 1.0 }, { depth: 2, ratePercent: 3.0 }, ...],
           tree:  [{ externalId: "A", parentExternalId: null }, { externalId: "B", parentExternalId: "A" }, ...]
         }
         → 200  { settlementId, tenantId, rates, nodeCount, updatedAt }

GET    /api/v1/tenants/{tenantId}/settlements/{settlementId}/config
```

> Every PUT replaces the entire tree (DELETE all + bulk INSERT in one transaction) and resets settlement status to OPEN if it was CALCULATED, invalidating any prior calculation.

### Purchases — batch submission

```
POST   /api/v1/tenants/{tenantId}/settlements/{settlementId}/purchases
         Body: { purchases: [{ buyerExternalId, amount, purchasedAt }] }
         → 202  { settlementId, tenantId, accepted, submittedAt }

GET    /api/v1/tenants/{tenantId}/settlements/{settlementId}/purchases?page=&size=
```

### Calculation — trigger (idempotent) + retrieve

```
POST   /api/v1/tenants/{tenantId}/settlements/{settlementId}/calculate
         Body: {} (empty — all inputs are already stored on the settlement)
         → 200  { calculationId, settlementId, tenantId, calculatedAt, cached,
                  results: [{ recipientExternalId, totalCommission }] }

GET    /api/v1/tenants/{tenantId}/settlements/{settlementId}/calculation
GET    /api/v1/tenants/{tenantId}/settlements/{settlementId}/calculation/recipients/{externalId}
GET    /api/v1/tenants/{tenantId}/settlements/{settlementId}/calculation/audit
         # returns full line-item audit trail: every CommissionLineItem row
```

### Approval — status transitions via API

```
POST   /api/v1/tenants/{tenantId}/settlements/{settlementId}/approve
         Body: {}
         → 200  { settlementId, status: "APPROVED" }

POST   /api/v1/tenants/{tenantId}/settlements/{settlementId}/reject
         Body: {}
         → 200  { settlementId, status: "REJECTED" }
```

> After rejection the settlement automatically reverts to OPEN, allowing corrections (new config or purchases) and a fresh calculation.

---

## Commission Rule Engine

### CommissionLineItem (audit-trail unit)

```kotlin
data class CommissionLineItem(
    val recipientExternalId: String,
    val sourcePurchaseId: Long?,     // null for aggregate rules not tied to one purchase
    val amount: BigDecimal,
    val depth: Int?,                 // null for non-depth-based rules
    val ruleId: String               // e.g. "depth-based-commission", "small-tenant-bonus"
)
```

### Strategy Interface

```kotlin
interface CommissionRule {
    val ruleId: String
    val order: Int get() = 100         // lower = higher priority
    fun isApplicable(context: CalculationContext): Boolean = true
    fun calculate(context: CalculationContext): List<CommissionLineItem>
}
```

### CalculationContext (built once per run, immutable)

```kotlin
data class CalculationContext(
    val tenantId: String,
    val settlement: Settlement,
    val settings: CommissionSettings,
    val ratesByDepth: Map<Int, BigDecimal>,      // depth -> rate% (pre-built for O(1) lookup)
    val treeMap: Map<String, TreeNodeMemento>,   // externalId -> node (full in-memory tree)
    val purchases: List<Purchase>,
    val totalRevenue: BigDecimal,                // pre-summed
    val nodeCount: Int
)
```

### Engine

Spring collects all `@Component` beans implementing `CommissionRule` via constructor injection (`List<CommissionRule>`), sorts by `order`, calls each `calculate()` in turn, and concatenates all `CommissionLineItem` lists into one flat list. The engine does **not** aggregate — raw line items are persisted as-is for the audit trail.

### Default: `DepthBasedCommissionRule` (order=100)

For each purchase: walk up the `treeMap` HashMap from buyer to root, stopping at the deepest configured depth. For each ancestor at depth N, emit one `CommissionLineItem(recipient=ancestor, sourcePurchaseId=purchase.id, amount=purchase.amount×rate, depth=N, ruleId="depth-based-commission")`. Pure in-memory, O(purchases × maxDepth).

### Pluggable example: `SmallTenantBonusRule` (order=200)

```kotlin
@Component
@ConditionalOnProperty("provisions.rules.small-tenant-bonus.enabled", havingValue = "true")
class SmallTenantBonusRule(...) : CommissionRule {
    override val ruleId = "small-tenant-bonus"
    override fun isApplicable(ctx: CalculationContext) = ctx.nodeCount < nodeThreshold
    override fun calculate(ctx: CalculationContext): List<CommissionLineItem> {
        val root = ctx.treeMap.values.first { it.parentExternalId == null }
        val bonus = ctx.totalRevenue * bonusPercent / 100
        // sourcePurchaseId = null (not tied to one purchase), depth = null
        return listOf(CommissionLineItem(root.externalId, null, bonus, null, ruleId))
    }
}
```

**Adding a new rule = add one `@Component` class. Zero other changes.**

---

## Idempotency Design

Calculation is **fully idempotent**: calling `POST .../calculate` any number of times with the same stored inputs (settings + purchases) always returns exactly the same result.

### Mechanism: input hashing

1. Before computing, `CalculationService` calls `InputHasher.compute(tenantId, settlementId)`:
   - Fetches current settings fingerprint (sorted depth→rate pairs as a canonical string)
   - Fetches all purchase IDs for the settlement, sorted ascending
   - Computes `SHA-256(tenantId + settlementId + settingsFingerprint + sortedPurchaseIds)`
2. Queries `calculation` table: `SELECT id FROM calculation WHERE tenant_id=? AND settlement_id=? AND input_hash=?`
3. **If found:** return the existing `calculationId` and its stored results immediately. Response includes `"cached": true`.
4. **If not found:** run the full engine, persist all `CommissionLineItem` rows, insert a new `calculation` row with the hash. Response includes `"cached": false`.

### Invariants guaranteed

- Same settings + same purchases → always same `calculationId` and same result rows.
- If settings or purchases change after a calculation, the hash changes → new calculation row is created; old results are preserved (historical audit).
- Re-submitting identical purchase batches is safe: `purchase` rows have a natural deduplication key (`buyer_external_id`, `amount`, `purchased_at`) surfaced as a UNIQUE constraint — duplicate submissions are rejected with `409 Conflict`.

### Settlement status transitions

```
OPEN ──(PUT config / POST purchases)───► OPEN
OPEN ──(POST calculate)────────────────► CALCULATED
CALCULATED ──(POST calculate, same)────► CALCULATED   (cached, no change)
CALCULATED ──(PUT config / purchases)──► OPEN         (invalidates calculation)
CALCULATED ──(POST approve)────────────► APPROVED
CALCULATED ──(POST reject)─────────────► REJECTED → OPEN  (auto, ready for correction)
APPROVED ──(no changes allowed)
LOCKED ──(calculate only)──────────────► CALCULATED
```

**Rules enforced at service layer:**
- `APPROVED`: all write operations return `409 Conflict` — settlement is frozen.
- `REJECTED`: immediately transitions to `OPEN` so corrections can begin.

---

## Key Service Signatures

```kotlin
// SettlementService
fun create(tenantId: String, request: CreateSettlementRequest): SettlementResponse
fun findById(tenantId: String, settlementId: Long): SettlementResponse
fun lock(tenantId: String, settlementId: Long): SettlementResponse   // OPEN → LOCKED

// SettingsService
@Transactional
fun configure(tenantId: String, settlementId: Long, request: ConfigureSettingsRequest): SettingsResponse
// → validate tree, delete all tree_node rows, bulk-insert new tree + upsert rates, reset status to OPEN

// TreeService
fun loadTreeIntoMemory(tenantId: String, settlementId: Long): Map<String, TreeNodeMemento>
// → single SQL SELECT, then O(n) HashMap build; no recursion, no CTE
fun validate(nodes: List<TreeNodeRequest>)
// → cycle detection, exactly one root, all parentExternalId refs exist

// PurchaseService
@Transactional
fun submitBatch(tenantId: String, settlementId: Long, request: SubmitPurchasesRequest): PurchaseResponse

// CalculationService — idempotent pipeline
@Transactional
fun calculate(tenantId: String, settlementId: Long): CalculationResponse
// 1. compute inputHash via InputHasher
// 2. check calculation table for existing hash → if found, return cached response
// 3. load settings + purchases + tree into CalculationContext
// 4. ruleEngine.run(context) → List<CommissionLineItem>
// 5. bulk-insert CommissionResult rows (batch_size=500)
// 6. insert Calculation row with inputHash + calculationId UUID
// 7. update settlement status → CALCULATED
// 8. return CalculationResponse (cached=false)

fun getResults(tenantId: String, settlementId: Long): CalculationResponse
fun getAuditTrail(tenantId: String, settlementId: Long): List<CommissionResultResponse>
fun getResultForRecipient(tenantId: String, settlementId: Long, recipientExternalId: String): CommissionResultResponse

// SettlementService (approve/reject added here — no separate service needed)
fun approve(tenantId: String, settlementId: Long): SettlementResponse
// → asserts status == CALCULATED, transitions to APPROVED

fun reject(tenantId: String, settlementId: Long): SettlementResponse
// → asserts status == CALCULATED, transitions to REJECTED → immediately OPEN
```

---

## Performance Strategy (5000+ nodes)

| Stage | Approach | Target |
|---|---|---|
| Idempotency check | Single indexed SELECT on `calculation` | < 5 ms |
| Tree load | Single `SELECT *` + O(n) HashMap | < 200 ms |
| Commission traversal | In-memory HashMap walk, O(purchases × maxDepth) | < 500 ms (10k purchases) |
| Result persist | `saveAll` + `hibernate.jdbc.batch_size=500` | < 500 ms |
| **Total (cold)** | No recursive SQL, no lazy-loading during hot loop | **< 2 s** |
| **Total (cached)** | Hash lookup only | **< 10 ms** |

---

## Testing Strategy

| Layer | Coverage | Tools |
|---|---|---|
| Unit | `DepthBasedCommissionRule`, `CommissionRuleEngine`, `TreeService`, `CalculationService`, `InputHasher` | JUnit 5 + MockK |
| Integration | All controllers, full HTTP round-trips, multi-tenant isolation, idempotency | `@SpringBootTest` + Testcontainers PostgreSQL |
| Idempotency | Call `calculate` twice → same `calculationId` returned, no duplicate rows | Integration test |
| Input change | Add purchase after calculation → new calculation run, old preserved | Integration test |
| Performance | 5000-node tree + 10k purchases → assert total < 5 s | JUnit 5 |
| Migration | All Flyway scripts apply clean to fresh schema | Testcontainers PostgreSQL |
| Approval flow | Approve happy path; reject → OPEN → recalculate → approve | Integration test |
| Frozen state | Config/purchase changes on APPROVED → 409 | Integration test |

---

## README Diagrams (to be added to README.md)

1. **System Context** — Mermaid C4: caller → microservice → PostgreSQL
2. **Commission Tree Example** — Mermaid graph: tree nodes + purchase → ancestor payouts with %
3. **API Flow** — Mermaid sequence: Configure → Submit Purchases → Calculate (with cache branch)
4. **Rule Engine** — Mermaid class diagram: `CommissionRule` strategy pattern + `CommissionLineItem`
5. **ER Diagram** — Mermaid `erDiagram`: all 7 tables with relationships
6. **Settlement Status FSM** — Mermaid state diagram: full lifecycle OPEN → CALCULATED → PENDING_APPROVAL → APPROVED / REJECTED

All in Mermaid (renders natively on GitHub).

---

## Architectural Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Multi-tenancy | `tenant_id` column + URL path segment | Column ensures DB-level isolation; path segment makes tenant scope explicit in every API call |
| Tree storage | Adjacency list | Simple schema; fast full-replace; single-query bulk load |
| Tree traversal | In-memory HashMap | No N+1 queries; no recursive CTE overhead; 5000 nodes ≈ 1 MB RAM |
| Rule return type | `List<CommissionLineItem>` | Full audit trail per purchase per rule; aggregation done at query time, not at write time |
| Idempotency | SHA-256 input hash on `calculation` table | Deterministic; database-enforced via UNIQUE constraint; O(1) cache lookup |
| Rule discovery | Spring `List<CommissionRule>` constructor injection | Zero-config extensibility: add `@Component` = automatically included |
| Tree replace | DELETE all + bulk INSERT in one `@Transactional` + reset status to OPEN | Matches "whole new tree" requirement; invalidates stale calculations automatically |
| Rounding | `BigDecimal` HALF_UP, 4 decimal places | Financial precision requirement |
| Result history | New `Calculation` row + new `CommissionResult` rows per run | Preserves full history; old results never overwritten |
| Approval/rejection | Status-only transitions on `settlement`, no separate table | Keeps it simple — no reviewer tracking needed |
| Rejection flow | REJECTED transitions immediately to OPEN | Unblocks corrections without a manual reset step |
| Frozen state | APPROVED blocks all write operations at service layer | Prevents changes to an approved settlement |
