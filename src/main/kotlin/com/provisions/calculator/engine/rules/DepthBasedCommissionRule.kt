package com.provisions.calculator.engine.rules

import com.provisions.calculator.engine.CalculationContext
import com.provisions.calculator.engine.CommissionLineItem
import com.provisions.calculator.engine.CommissionRule
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

@Component
class DepthBasedCommissionRule : CommissionRule {

    private val log = LoggerFactory.getLogger(DepthBasedCommissionRule::class.java)

    override val ruleId: String = "DEPTH_BASED"
    override val order: Int = 100

    override fun calculate(context: CalculationContext): List<CommissionLineItem> {
        val results = mutableListOf<CommissionLineItem>()
        val maxDepth = context.ratesByDepth.keys.maxOrNull() ?: return results

        for (purchase in context.purchases) {
            if (purchase.buyerCustomerId !in context.treeMap) {
                log.warn("Buyer '{}' (purchase {}) not found in tree for settlement {} — skipping commission",
                    purchase.buyerCustomerId, purchase.id, context.settlement.id)
                continue
            }
            var currentCustomerId: String? = context.treeMap[purchase.buyerCustomerId]?.parentCustomerId
            var depth = 1

            while (currentCustomerId != null && depth <= maxDepth) {
                val rate = context.ratesByDepth[depth]
                if (rate != null) {
                    val commission = purchase.amount
                        .multiply(rate)
                        .divide(BigDecimal(100), 4, RoundingMode.HALF_UP)

                    results.add(
                        CommissionLineItem(
                            recipientCustomerId = currentCustomerId,
                            sourcePurchaseId = purchase.id,
                            amount = commission,
                            depth = depth,
                            ruleId = ruleId
                        )
                    )
                }

                currentCustomerId = context.treeMap[currentCustomerId]?.parentCustomerId
                depth++
            }
        }

        return results
    }
}
