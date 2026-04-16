package com.provisions.calculator.service

import com.provisions.calculator.model.SettlementStatus
import com.provisions.calculator.repository.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

@Service
@Transactional(readOnly = true)
class MetricsService(
    private val settlementRepository: SettlementRepository,
    private val purchaseRepository: PurchaseRepository,
    private val commissionResultRepository: CommissionResultRepository,
    private val calculationRepository: CalculationRepository,
    private val commissionSettingsRepository: CommissionSettingsRepository
) {

    fun getTenantOverview(tenantId: String): TenantOverview {
        val statusCounts = settlementRepository.countByStatusForTenant(tenantId)
            .associate { row -> (row[0] as SettlementStatus).name to (row[1] as Long).toInt() }

        val totalPurchaseVolume = purchaseRepository.sumAmountByTenantId(tenantId)
        val totalCommission = commissionResultRepository.sumAmountForLatestCalculationsByTenantId(tenantId)

        val commissionRate = if (totalPurchaseVolume > BigDecimal.ZERO) {
            totalCommission.multiply(BigDecimal(100)).divide(totalPurchaseVolume, 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        return TenantOverview(
            settlementsByStatus = statusCounts,
            totalPurchaseVolume = totalPurchaseVolume,
            totalCommission = totalCommission,
            averageCommissionRatePercent = commissionRate
        )
    }

    fun getSettlementMetrics(tenantId: String, settlementId: Long): SettlementMetrics {
        settlementRepository.findByTenantIdAndId(tenantId, settlementId)
            ?: throw org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Settlement not found"
            )

        val purchases = purchaseRepository.findByTenantIdAndSettlementId(tenantId, settlementId)
        val purchaseAnalysis = analyzePurchases(purchases)

        val latestCalc = calculationRepository.findFirstByTenantIdAndSettlementIdOrderByCalculatedAtDesc(tenantId, settlementId)
        val commissionAnalysis: CommissionAnalysis?
        val crossCheck: CrossCheckResult?

        if (latestCalc != null) {
            val results = commissionResultRepository.findByCalculationId(latestCalc.id)
            commissionAnalysis = analyzeCommissions(results)

            val settings = commissionSettingsRepository.findByTenantIdAndSettlementId(tenantId, settlementId)
            crossCheck = if (settings != null) {
                computeCrossCheck(purchaseAnalysis.totalAmount, settings.rates, commissionAnalysis.totalCommission)
            } else null
        } else {
            commissionAnalysis = null
            crossCheck = null
        }

        return SettlementMetrics(
            purchases = purchaseAnalysis,
            commissions = commissionAnalysis,
            crossCheck = crossCheck
        )
    }

    private fun analyzePurchases(purchases: List<com.provisions.calculator.model.Purchase>): PurchaseAnalysis {
        if (purchases.isEmpty()) {
            return PurchaseAnalysis(
                totalAmount = BigDecimal.ZERO, count = 0,
                average = BigDecimal.ZERO, min = BigDecimal.ZERO, max = BigDecimal.ZERO,
                stdDev = BigDecimal.ZERO, outliers = emptyList()
            )
        }

        val amounts = purchases.map { it.amount }
        val count = amounts.size
        val total = amounts.fold(BigDecimal.ZERO) { acc, a -> acc.add(a) }
        val avg = total.divide(BigDecimal(count), 4, RoundingMode.HALF_UP)
        val min = amounts.min()
        val max = amounts.max()

        val variance = amounts.fold(BigDecimal.ZERO) { acc, a ->
            val diff = a.subtract(avg)
            acc.add(diff.multiply(diff))
        }.divide(BigDecimal(count), 8, RoundingMode.HALF_UP)

        val stdDev = BigDecimal(Math.sqrt(variance.toDouble()), MathContext(8))
            .setScale(4, RoundingMode.HALF_UP)

        val outliers = if (stdDev > BigDecimal.ZERO) {
            val threshold = stdDev.multiply(BigDecimal(2))
            purchases.filter { p ->
                p.amount.subtract(avg).abs() > threshold
            }.map { p ->
                val deviation = if (stdDev > BigDecimal.ZERO) {
                    p.amount.subtract(avg).divide(stdDev, 2, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO
                PurchaseOutlier(
                    purchaseId = p.id,
                    buyerCustomerId = p.buyerCustomerId,
                    amount = p.amount,
                    deviationFactor = deviation
                )
            }
        } else emptyList()

        return PurchaseAnalysis(
            totalAmount = total, count = count,
            average = avg, min = min, max = max,
            stdDev = stdDev, outliers = outliers
        )
    }

    private fun analyzeCommissions(results: List<com.provisions.calculator.model.CommissionResult>): CommissionAnalysis {
        if (results.isEmpty()) {
            return CommissionAnalysis(
                totalCommission = BigDecimal.ZERO,
                recipientCount = 0,
                byDepth = emptyList(),
                outliers = emptyList()
            )
        }

        val totalCommission = results.fold(BigDecimal.ZERO) { acc, r -> acc.add(r.amount) }

        val byDepth = results.groupBy { it.depth }
            .map { (depth, items) ->
                DepthBucket(
                    depth = depth,
                    totalAmount = items.fold(BigDecimal.ZERO) { acc, r -> acc.add(r.amount) },
                    count = items.size
                )
            }
            .sortedBy { it.depth }

        val recipientTotals = results.groupBy { it.recipientCustomerId }
            .map { (customerId, items) ->
                customerId to items.fold(BigDecimal.ZERO) { acc, r -> acc.add(r.amount) }
            }

        val recipientCount = recipientTotals.size

        val outliers = if (recipientTotals.size > 1) {
            val amounts = recipientTotals.map { it.second }
            val avg = amounts.fold(BigDecimal.ZERO) { acc, a -> acc.add(a) }
                .divide(BigDecimal(amounts.size), 4, RoundingMode.HALF_UP)
            val variance = amounts.fold(BigDecimal.ZERO) { acc, a ->
                val diff = a.subtract(avg)
                acc.add(diff.multiply(diff))
            }.divide(BigDecimal(amounts.size), 8, RoundingMode.HALF_UP)
            val stdDev = BigDecimal(Math.sqrt(variance.toDouble()), MathContext(8))
                .setScale(4, RoundingMode.HALF_UP)

            if (stdDev > BigDecimal.ZERO) {
                val threshold = stdDev.multiply(BigDecimal(2))
                recipientTotals.filter { (_, total) ->
                    total.subtract(avg).abs() > threshold
                }.map { (customerId, total) ->
                    CommissionOutlier(
                        recipientCustomerId = customerId,
                        totalCommission = total,
                        deviationFactor = total.subtract(avg).divide(stdDev, 2, RoundingMode.HALF_UP)
                    )
                }
            } else emptyList()
        } else emptyList()

        return CommissionAnalysis(
            totalCommission = totalCommission,
            recipientCount = recipientCount,
            byDepth = byDepth,
            outliers = outliers
        )
    }

    private fun computeCrossCheck(
        totalPurchaseVolume: BigDecimal,
        rates: List<com.provisions.calculator.model.CommissionRate>,
        actualTotalCommission: BigDecimal
    ): CrossCheckResult {
        val depthLines = rates.sortedBy { it.depth }.map { rate ->
            val theoreticalAmount = totalPurchaseVolume
                .multiply(rate.ratePercent)
                .divide(BigDecimal(100), 4, RoundingMode.HALF_UP)
            TheoreticalDepthLine(
                depth = rate.depth,
                ratePercent = rate.ratePercent,
                amount = theoreticalAmount
            )
        }

        val theoreticalTotal = depthLines.fold(BigDecimal.ZERO) { acc, line -> acc.add(line.amount) }

        val deviationPercent = if (theoreticalTotal > BigDecimal.ZERO) {
            actualTotalCommission.subtract(theoreticalTotal)
                .multiply(BigDecimal(100))
                .divide(theoreticalTotal, 2, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        return CrossCheckResult(
            totalPurchaseVolume = totalPurchaseVolume,
            theoreticalByDepth = depthLines,
            theoreticalTotal = theoreticalTotal,
            actualTotal = actualTotalCommission,
            deviationPercent = deviationPercent
        )
    }

    // --- DTOs ---

    data class TenantOverview(
        val settlementsByStatus: Map<String, Int>,
        val totalPurchaseVolume: BigDecimal,
        val totalCommission: BigDecimal,
        val averageCommissionRatePercent: BigDecimal
    )

    data class SettlementMetrics(
        val purchases: PurchaseAnalysis,
        val commissions: CommissionAnalysis?,
        val crossCheck: CrossCheckResult?
    )

    data class PurchaseAnalysis(
        val totalAmount: BigDecimal,
        val count: Int,
        val average: BigDecimal,
        val min: BigDecimal,
        val max: BigDecimal,
        val stdDev: BigDecimal,
        val outliers: List<PurchaseOutlier>
    )

    data class PurchaseOutlier(
        val purchaseId: Long,
        val buyerCustomerId: String,
        val amount: BigDecimal,
        val deviationFactor: BigDecimal
    )

    data class CommissionAnalysis(
        val totalCommission: BigDecimal,
        val recipientCount: Int,
        val byDepth: List<DepthBucket>,
        val outliers: List<CommissionOutlier>
    )

    data class DepthBucket(
        val depth: Int?,
        val totalAmount: BigDecimal,
        val count: Int
    )

    data class CommissionOutlier(
        val recipientCustomerId: String,
        val totalCommission: BigDecimal,
        val deviationFactor: BigDecimal
    )

    data class CrossCheckResult(
        val totalPurchaseVolume: BigDecimal,
        val theoreticalByDepth: List<TheoreticalDepthLine>,
        val theoreticalTotal: BigDecimal,
        val actualTotal: BigDecimal,
        val deviationPercent: BigDecimal
    )

    data class TheoreticalDepthLine(
        val depth: Int,
        val ratePercent: BigDecimal,
        val amount: BigDecimal
    )
}
