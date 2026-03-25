package com.provisions.calculator.service

import com.provisions.calculator.engine.CalculationContext
import com.provisions.calculator.engine.CommissionRuleEngine
import com.provisions.calculator.model.Calculation
import com.provisions.calculator.model.CommissionResult
import com.provisions.calculator.model.SettlementStatus
import com.provisions.calculator.repository.*
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.security.MessageDigest

@Service
class CalculationService(
    private val settlementService: SettlementService,
    private val treeService: TreeService,
    private val purchaseRepository: PurchaseRepository,
    private val commissionSettingsRepository: CommissionSettingsRepository,
    private val calculationRepository: CalculationRepository,
    private val commissionResultRepository: CommissionResultRepository,
    private val settlementRepository: SettlementRepository,
    private val ruleEngine: CommissionRuleEngine
) {

    @Transactional
    fun calculate(tenantId: String, settlementId: Long): CalculationResult {
        val settlement = settlementService.findById(tenantId, settlementId)
        settlementService.guardNotApproved(settlement)

        val settings = commissionSettingsRepository.findByTenantIdAndSettlementId(tenantId, settlementId)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No commission settings configured")

        val purchases = purchaseRepository.findByTenantIdAndSettlementId(tenantId, settlementId)
        if (purchases.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No purchases to calculate")
        }

        val ratesByDepth = settings.rates.associate { it.depth to it.ratePercent }

        // Compute input hash
        val inputHash = computeInputHash(tenantId, settlementId, ratesByDepth, purchases.map { it.id }.sorted())

        // Idempotency check
        val existing = calculationRepository.findByTenantIdAndSettlementIdAndInputHash(tenantId, settlementId, inputHash)
        if (existing != null) {
            val results = commissionResultRepository.findByCalculationId(existing.id)
            return CalculationResult(
                calculation = existing,
                results = results,
                fromCache = true
            )
        }

        // Build context
        val treeMap = treeService.loadTreeIntoMemory(tenantId, settlementId)
        val totalRevenue = purchases.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.amount) }

        val context = CalculationContext(
            tenantId = tenantId,
            settlement = settlement,
            ratesByDepth = ratesByDepth,
            treeMap = treeMap,
            purchases = purchases,
            totalRevenue = totalRevenue,
            nodeCount = treeMap.size
        )

        // Run engine
        val lineItems = ruleEngine.execute(context)

        // Persist calculation
        val calculation = Calculation(
            tenantId = tenantId,
            settlement = settlement,
            inputHash = inputHash
        )
        calculationRepository.save(calculation)

        // Persist results
        val purchaseMap = purchases.associateBy { it.id }
        val commissionResults = lineItems.map { item ->
            CommissionResult(
                tenantId = tenantId,
                settlement = settlement,
                calculation = calculation,
                recipientCustomerId = item.recipientCustomerId,
                sourcePurchase = item.sourcePurchaseId?.let { purchaseMap[it] },
                amount = item.amount,
                depth = item.depth,
                ruleId = item.ruleId
            )
        }
        commissionResultRepository.saveAll(commissionResults)

        // Update settlement status
        settlement.status = SettlementStatus.CALCULATED
        settlementRepository.save(settlement)

        return CalculationResult(
            calculation = calculation,
            results = commissionResults,
            fromCache = false
        )
    }

    fun getResults(tenantId: String, settlementId: Long): CalculationResult {
        settlementService.findById(tenantId, settlementId)
        val calculation = calculationRepository.findFirstByTenantIdAndSettlementIdOrderByCalculatedAtDesc(tenantId, settlementId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No calculation found")
        val results = commissionResultRepository.findByCalculationId(calculation.id)
        return CalculationResult(calculation = calculation, results = results, fromCache = true)
    }

    fun getResultForRecipient(tenantId: String, settlementId: Long, recipientCustomerId: String): List<CommissionResult> {
        settlementService.findById(tenantId, settlementId)
        val calculation = calculationRepository.findFirstByTenantIdAndSettlementIdOrderByCalculatedAtDesc(tenantId, settlementId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No calculation found")
        return commissionResultRepository.findByCalculationIdAndRecipientCustomerId(calculation.id, recipientCustomerId)
    }

    fun getAuditTrail(tenantId: String, settlementId: Long): List<CommissionResult> {
        settlementService.findById(tenantId, settlementId)
        val calculation = calculationRepository.findFirstByTenantIdAndSettlementIdOrderByCalculatedAtDesc(tenantId, settlementId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No calculation found")
        return commissionResultRepository.findByCalculationId(calculation.id)
    }

    private fun computeInputHash(
        tenantId: String,
        settlementId: Long,
        ratesByDepth: Map<Int, BigDecimal>,
        sortedPurchaseIds: List<Long>
    ): String {
        val sortedRates = ratesByDepth.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }
        val purchaseIds = sortedPurchaseIds.joinToString(",")
        val input = "$tenantId|$settlementId|$sortedRates|$purchaseIds"

        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    data class CalculationResult(
        val calculation: Calculation,
        val results: List<CommissionResult>,
        val fromCache: Boolean
    )
}
