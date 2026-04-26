package com.provisions.calculator.service

import com.provisions.calculator.engine.CalculationContext
import com.provisions.calculator.engine.CommissionRule
import com.provisions.calculator.engine.CommissionRuleEngine
import com.provisions.calculator.model.Calculation
import com.provisions.calculator.model.CommissionResult
import com.provisions.calculator.model.Settlement
import com.provisions.calculator.model.SettlementStatus
import com.provisions.calculator.repository.*
import org.springframework.dao.DataIntegrityViolationException
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
    private val ruleEngine: CommissionRuleEngine,
    private val commissionRules: List<CommissionRule>
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

        val treeMap = treeService.loadTreeIntoMemory(tenantId, settlementId)

        val sortedRuleIds = commissionRules.sortedBy { it.ruleId }.map { it.ruleId }
        val inputHash = computeInputHash(tenantId, settlementId, ratesByDepth, purchases.map { it.id }.sorted(), treeMap, sortedRuleIds)

        val existing = calculationRepository.findByTenantIdAndSettlementIdAndInputHash(tenantId, settlementId, inputHash)
        if (existing != null) {
            markSettlementCalculated(settlement)
            val results = commissionResultRepository.findByCalculationId(existing.id)
            return CalculationResult(calculation = existing, results = results, fromCache = true)
        }

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

        val lineItems = ruleEngine.execute(context)

        val calculation = Calculation(
            tenantId = tenantId,
            settlement = settlement,
            inputHash = inputHash
        )
        try {
            calculationRepository.save(calculation)
            calculationRepository.flush()
        } catch (e: DataIntegrityViolationException) {
            val concurrentCalc = calculationRepository.findByTenantIdAndSettlementIdAndInputHash(tenantId, settlementId, inputHash)
                ?: throw e
            markSettlementCalculated(settlement)
            val results = commissionResultRepository.findByCalculationId(concurrentCalc.id)
            return CalculationResult(calculation = concurrentCalc, results = results, fromCache = true)
        }

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

        markSettlementCalculated(settlement)

        return CalculationResult(
            calculation = calculation,
            results = commissionResults,
            fromCache = false
        )
    }

    @Transactional(readOnly = true)
    fun getResults(tenantId: String, settlementId: Long): CalculationResult {
        settlementService.findById(tenantId, settlementId)
        val calculation = calculationRepository.findFirstByTenantIdAndSettlementIdOrderByCalculatedAtDesc(tenantId, settlementId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No calculation found")
        val results = commissionResultRepository.findByCalculationId(calculation.id)
        return CalculationResult(calculation = calculation, results = results, fromCache = true)
    }

    @Transactional(readOnly = true)
    fun getResultForRecipient(tenantId: String, settlementId: Long, recipientCustomerId: String): List<CommissionResult> {
        settlementService.findById(tenantId, settlementId)
        val calculation = calculationRepository.findFirstByTenantIdAndSettlementIdOrderByCalculatedAtDesc(tenantId, settlementId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No calculation found")
        return commissionResultRepository.findByCalculationIdAndRecipientCustomerId(calculation.id, recipientCustomerId)
    }

    @Transactional(readOnly = true)
    fun getAuditTrail(tenantId: String, settlementId: Long): List<CommissionResult> {
        settlementService.findById(tenantId, settlementId)
        val calculation = calculationRepository.findFirstByTenantIdAndSettlementIdOrderByCalculatedAtDesc(tenantId, settlementId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No calculation found")
        return commissionResultRepository.findByCalculationId(calculation.id)
    }

    private fun markSettlementCalculated(settlement: Settlement) {
        if (settlement.status != SettlementStatus.CALCULATED) {
            settlement.status = SettlementStatus.CALCULATED
            settlementRepository.save(settlement)
        }
    }

    private fun computeInputHash(
        tenantId: String,
        settlementId: Long,
        ratesByDepth: Map<Int, BigDecimal>,
        sortedPurchaseIds: List<Long>,
        treeMap: Map<String, com.provisions.calculator.engine.TreeNodeMemento>,
        sortedRuleIds: List<String>
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("$tenantId|$settlementId|".toByteArray())
        for ((depth, rate) in ratesByDepth.entries.sortedBy { it.key }) {
            digest.update("$depth:$rate,".toByteArray())
        }
        digest.update("|".toByteArray())
        for (id in sortedPurchaseIds) {
            digest.update("$id,".toByteArray())
        }
        digest.update("|".toByteArray())
        for ((nodeId, memento) in treeMap.entries.sortedBy { it.key }) {
            digest.update("$nodeId:${memento.parentCustomerId ?: "ROOT"},".toByteArray())
        }
        digest.update("|".toByteArray())
        for (ruleId in sortedRuleIds) {
            digest.update("$ruleId,".toByteArray())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    data class CalculationResult(
        val calculation: Calculation,
        val results: List<CommissionResult>,
        val fromCache: Boolean
    )
}
