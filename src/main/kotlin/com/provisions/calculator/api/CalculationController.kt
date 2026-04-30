package com.provisions.calculator.api

import com.provisions.calculator.service.CalculationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.Instant
import java.util.*

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/settlements/{settlementId}")
@Tag(name = "Calculation")
class CalculationController(private val calculationService: CalculationService) {

    @PostMapping("/calculate")
    @Operation(
        summary = "Calculate commissions",
        description = """Triggers commission calculation for all purchases in this settlement.

For each purchase, the engine walks up the referral tree from the buyer and applies the configured rate at each depth level.

**Idempotent:** Calling this again with the same data returns the cached result (fromCache = true). The cache is invalidated when purchases, rates, tree structure, or rules change."""
    )
    fun calculate(
        @Parameter(description = "Tenant identifier", example = "acme") @PathVariable tenantId: String,
        @PathVariable settlementId: Long
    ): CalculationResponse {
        return toResponse(calculationService.calculate(tenantId, settlementId))
    }

    @GetMapping("/calculation")
    @Operation(
        summary = "Get aggregated results",
        description = "Returns the commission totals per recipient from the most recent calculation."
    )
    fun getResults(
        @Parameter(description = "Tenant identifier", example = "acme") @PathVariable tenantId: String,
        @PathVariable settlementId: Long
    ): CalculationResponse {
        return toResponse(calculationService.getResults(tenantId, settlementId))
    }

    @GetMapping("/calculation/recipients/{customerId}")
    @Operation(
        summary = "Get results for a single recipient",
        description = "Returns the detailed commission breakdown for one recipient — showing each source purchase, amount, depth, and rule that generated the commission."
    )
    fun getResultForRecipient(
        @Parameter(description = "Tenant identifier", example = "acme") @PathVariable tenantId: String,
        @PathVariable settlementId: Long,
        @Parameter(description = "Customer ID of the commission recipient", example = "alice") @PathVariable customerId: String
    ): RecipientDetailResponse {
        val results = calculationService.getResultForRecipient(tenantId, settlementId, customerId)
        val total = results.fold(BigDecimal.ZERO) { acc, r -> acc.add(r.amount) }
        return RecipientDetailResponse(
            customerId = customerId,
            totalCommission = total,
            details = results.map {
                CommissionDetail(
                    sourcePurchaseId = it.sourcePurchase?.id,
                    amount = it.amount,
                    depth = it.depth,
                    ruleId = it.ruleId
                )
            }
        )
    }

    @GetMapping("/calculation/audit")
    @Operation(
        summary = "Get full audit trail",
        description = "Returns every individual commission line item — one entry per purchase-recipient-depth combination. Useful for reconciliation and compliance."
    )
    fun getAuditTrail(
        @Parameter(description = "Tenant identifier", example = "acme") @PathVariable tenantId: String,
        @PathVariable settlementId: Long
    ): List<AuditEntry> {
        return calculationService.getAuditTrail(tenantId, settlementId).map {
            AuditEntry(
                recipientCustomerId = it.recipientCustomerId,
                sourcePurchaseId = it.sourcePurchase?.id,
                amount = it.amount,
                depth = it.depth,
                ruleId = it.ruleId
            )
        }
    }

    private fun toResponse(result: CalculationService.CalculationResult): CalculationResponse {
        val aggregated = result.results
            .groupBy { it.recipientCustomerId }
            .map { (customerId, items) ->
                RecipientTotal(customerId, items.fold(BigDecimal.ZERO) { acc, r -> acc.add(r.amount) })
            }
        return CalculationResponse(
            calculationId = result.calculation.id,
            settlementId = result.calculation.settlement.id,
            calculatedAt = result.calculation.calculatedAt,
            fromCache = result.fromCache,
            results = aggregated
        )
    }
}

data class CalculationResponse(
    val calculationId: UUID,
    val settlementId: Long,
    val calculatedAt: Instant,
    val fromCache: Boolean,
    val results: List<RecipientTotal>
)

data class RecipientTotal(val customerId: String, val totalCommission: BigDecimal)

data class RecipientDetailResponse(
    val customerId: String,
    val totalCommission: BigDecimal,
    val details: List<CommissionDetail>
)

data class CommissionDetail(val sourcePurchaseId: Long?, val amount: BigDecimal, val depth: Int?, val ruleId: String)

data class AuditEntry(
    val recipientCustomerId: String,
    val sourcePurchaseId: Long?,
    val amount: BigDecimal,
    val depth: Int?,
    val ruleId: String
)
