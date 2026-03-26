package com.provisions.calculator.engine

import com.provisions.calculator.model.Purchase
import com.provisions.calculator.model.Settlement
import com.provisions.calculator.model.SettlementStatus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class CommissionRuleEngineTest {

    private fun createContext() = CalculationContext(
        tenantId = "tenant1",
        settlement = Settlement(id = 1, tenantId = "tenant1", name = "Test", status = SettlementStatus.OPEN),
        ratesByDepth = mapOf(1 to BigDecimal("1.0")),
        treeMap = mapOf("A" to TreeNodeMemento("A", null, listOf("B")), "B" to TreeNodeMemento("B", "A", emptyList())),
        purchases = listOf(
            Purchase(id = 1, tenantId = "tenant1",
                settlement = Settlement(id = 1, tenantId = "tenant1", name = "Test", status = SettlementStatus.OPEN),
                buyerCustomerId = "B", amount = BigDecimal("100.0000"), purchasedAt = LocalDateTime.now())
        ),
        totalRevenue = BigDecimal("100.0000"),
        nodeCount = 2
    )

    @Test
    fun `rules are sorted by order and results concatenated`() {
        val rule1 = object : CommissionRule {
            override val ruleId = "RULE_200"
            override val order = 200
            override fun calculate(context: CalculationContext) = listOf(
                CommissionLineItem("A", 1L, BigDecimal("2.0000"), null, ruleId)
            )
        }
        val rule2 = object : CommissionRule {
            override val ruleId = "RULE_50"
            override val order = 50
            override fun calculate(context: CalculationContext) = listOf(
                CommissionLineItem("A", 1L, BigDecimal("1.0000"), null, ruleId)
            )
        }

        val engine = CommissionRuleEngine(listOf(rule1, rule2))
        val results = engine.execute(createContext())

        assertEquals(2, results.size)
        // rule2 (order 50) should execute first
        assertEquals("RULE_50", results[0].ruleId)
        assertEquals("RULE_200", results[1].ruleId)
    }

    @Test
    fun `isApplicable filters out non-applicable rules`() {
        val applicableRule = object : CommissionRule {
            override val ruleId = "APPLICABLE"
            override fun calculate(context: CalculationContext) = listOf(
                CommissionLineItem("A", 1L, BigDecimal("1.0000"), null, ruleId)
            )
        }
        val notApplicableRule = object : CommissionRule {
            override val ruleId = "NOT_APPLICABLE"
            override fun isApplicable(context: CalculationContext) = false
            override fun calculate(context: CalculationContext) = listOf(
                CommissionLineItem("A", 1L, BigDecimal("99.0000"), null, ruleId)
            )
        }

        val engine = CommissionRuleEngine(listOf(applicableRule, notApplicableRule))
        val results = engine.execute(createContext())

        assertEquals(1, results.size)
        assertEquals("APPLICABLE", results[0].ruleId)
    }

    @Test
    fun `empty rules list produces empty results`() {
        val engine = CommissionRuleEngine(emptyList())
        val results = engine.execute(createContext())
        assertTrue(results.isEmpty())
    }
}
