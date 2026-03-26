package com.provisions.calculator.api

import com.provisions.calculator.service.CalculationService
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/settlements/{settlementId}")
class CalculationController(private val calculationService: CalculationService) {

    @PostMapping("/calculate")
    fun calculate(
        @PathVariable tenantId: String,
        @PathVariable settlementId: Long
    ): CalculationResponse {
        return toResponse(calculationService.calculate(tenantId, settlementId))
    }

    @GetMapping("/calculation")
    fun getResults(
        @PathVariable tenantId: String,
        @PathVariable settlementId: Long
    ): CalculationResponse {
        return toResponse(calculationService.getResults(tenantId, settlementId))
    }

    @GetMapping("/calculation/recipients/{customerId}")
    fun getResultForRecipient(
        @PathVariable tenantId: String,
        @PathVariable settlementId: Long,
        @PathVariable customerId: String
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
    fun getAuditTrail(
        @PathVariable tenantId: String,
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
    val calculatedAt: LocalDateTime,
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
