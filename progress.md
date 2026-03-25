# Implementation Progress

## 1. Project Setup
- [ ] Initialize Gradle project with Kotlin DSL (`build.gradle.kts`)
- [ ] Add dependencies: Spring Boot Web, Spring Data JPA, PostgreSQL driver, Flyway, Jakarta Validation
- [ ] Configure `application.yml`: datasource, JPA, Flyway, server port
- [ ] Verify Spring Boot app starts with empty `main()`

## 2. Database Migrations
- [ ] `V1__create_settlement.sql`
- [ ] `V2__create_commission_settings_and_rates.sql`
- [ ] `V3__create_tree_node.sql`
- [ ] `V4__create_purchase.sql`
- [ ] `V5__create_calculation.sql`
- [ ] `V6__create_commission_result.sql`
- [ ] `V7__add_indexes.sql`
- [ ] Verify all migrations apply cleanly on first startup

## 3. Domain Models
- [ ] `Settlement.kt` + `SettlementStatus` enum (OPEN / CALCULATED / APPROVED / REJECTED)
- [ ] `CommissionSettings.kt`
- [ ] `CommissionRate.kt`
- [ ] `TreeNode.kt`
- [ ] `Purchase.kt`
- [ ] `Calculation.kt`
- [ ] `CommissionResult.kt`

## 4. Repositories
- [ ] `SettlementRepository`
- [ ] `CommissionSettingsRepository`
- [ ] `TreeNodeRepository`
- [ ] `PurchaseRepository`
- [ ] `CalculationRepository`
- [ ] `CommissionResultRepository`

## 5. Rule Engine
- [ ] `CommissionLineItem.kt` data class
- [ ] `TreeNodeMemento` + `CalculationContext.kt`
- [ ] `CommissionRule.kt` interface
- [ ] `CommissionRuleEngine.kt`
- [ ] `DepthBasedCommissionRule.kt`

## 6. Services
- [ ] `TreeService` — load tree into `HashMap` + validate (cycle detection, single root)
- [ ] `SettlementService` — create, findById, list, configure (tree + rates), approve, reject
- [ ] `PurchaseService` — submitBatch
- [ ] `CalculationService` — idempotency check (input hash), run engine, persist results, getResults, getAuditTrail, getResultForRecipient

## 7. API Layer
- [ ] `SettlementController` — POST, GET, GET list, PUT config, GET config, POST approve, POST reject
- [ ] `PurchaseController` — POST batch, GET list (paginated)
- [ ] `CalculationController` — POST calculate, GET result, GET recipient result, GET audit
- [ ] Request classes: `CreateSettlementRequest`, `ConfigureSettingsRequest`, `TreeNodeRequest`, `SubmitPurchasesRequest`
- [ ] `GlobalExceptionHandler` — map service exceptions to HTTP status codes

## 8. Tests
- [ ] Unit: `DepthBasedCommissionRule` — depth 1/2/3, rounding, buyer at root, multiple purchases
- [ ] Unit: `CommissionRuleEngine` — rule ordering, `isApplicable` filtering, result concatenation
- [ ] Unit: `TreeService` — valid tree, cycle detection, multiple roots rejected
- [ ] Unit: `CalculationService` — idempotency (same hash → cached), new hash → fresh run
- [ ] Integration: settlement lifecycle (create → config → purchases → calculate → approve)
- [ ] Integration: reject → auto-reset to OPEN → recalculate → approve
- [ ] Integration: APPROVED settlement blocks all writes (409)
- [ ] Integration: multi-tenant isolation — tenant A cannot read tenant B data
- [ ] Integration: idempotency — POST calculate twice → same `calculationId`, no duplicate rows
- [ ] Migration test: all Flyway scripts apply clean on fresh schema (Testcontainers)
- [ ] Performance test: 5000-node tree + 10k purchases completes in < 5s
