package com.provisions.calculator.engine.rules

import com.provisions.calculator.engine.CalculationContext
import com.provisions.calculator.engine.TreeNodeMemento
import com.provisions.calculator.model.Purchase
import com.provisions.calculator.model.Settlement
import com.provisions.calculator.model.SettlementStatus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class DepthBasedCommissionRuleTest {

    private val rule = DepthBasedCommissionRule()

    private fun createSettlement() = Settlement(
        id = 1L,
        tenantId = "tenant1",
        name = "Test",
        status = SettlementStatus.OPEN
    )

    private fun createPurchase(id: Long, buyerCustomerId: String, amount: BigDecimal) = Purchase(
        id = id,
        tenantId = "tenant1",
        settlement = createSettlement(),
        buyerCustomerId = buyerCustomerId,
        amount = amount,
        purchasedAt = Instant.now()
    )

    // Tree:  A -> B -> C -> D
    private fun createTreeMap() = mapOf(
        "A" to TreeNodeMemento("A", null, listOf("B")),
        "B" to TreeNodeMemento("B", "A", listOf("C")),
        "C" to TreeNodeMemento("C", "B", listOf("D")),
        "D" to TreeNodeMemento("D", "C", emptyList())
    )

    @Test
    fun `depth 1 commission from direct parent`() {
        val context = CalculationContext(
            tenantId = "tenant1",
            settlement = createSettlement(),
            ratesByDepth = mapOf(1 to BigDecimal("1.0")),
            treeMap = createTreeMap(),
            purchases = listOf(createPurchase(1, "B", BigDecimal("100.0000"))),
            totalRevenue = BigDecimal("100.0000"),
            nodeCount = 4
        )

        val results = rule.calculate(context)

        assertEquals(1, results.size)
        assertEquals("A", results[0].recipientCustomerId)
        assertEquals(BigDecimal("1.0000"), results[0].amount)
        assertEquals(1, results[0].depth)
    }

    @Test
    fun `depth 1, 2, 3 commission walks up the tree`() {
        val context = CalculationContext(
            tenantId = "tenant1",
            settlement = createSettlement(),
            ratesByDepth = mapOf(
                1 to BigDecimal("1.0"),
                2 to BigDecimal("3.0"),
                3 to BigDecimal("5.0")
            ),
            treeMap = createTreeMap(),
            purchases = listOf(createPurchase(1, "D", BigDecimal("200.0000"))),
            totalRevenue = BigDecimal("200.0000"),
            nodeCount = 4
        )

        val results = rule.calculate(context)

        assertEquals(3, results.size)

        // Depth 1: C gets 1% of 200 = 2.0000
        val depth1 = results.find { it.depth == 1 }!!
        assertEquals("C", depth1.recipientCustomerId)
        assertEquals(BigDecimal("2.0000"), depth1.amount)

        // Depth 2: B gets 3% of 200 = 6.0000
        val depth2 = results.find { it.depth == 2 }!!
        assertEquals("B", depth2.recipientCustomerId)
        assertEquals(BigDecimal("6.0000"), depth2.amount)

        // Depth 3: A gets 5% of 200 = 10.0000
        val depth3 = results.find { it.depth == 3 }!!
        assertEquals("A", depth3.recipientCustomerId)
        assertEquals(BigDecimal("10.0000"), depth3.amount)
    }

    @Test
    fun `buyer at root produces no commission`() {
        val context = CalculationContext(
            tenantId = "tenant1",
            settlement = createSettlement(),
            ratesByDepth = mapOf(1 to BigDecimal("1.0")),
            treeMap = createTreeMap(),
            purchases = listOf(createPurchase(1, "A", BigDecimal("100.0000"))),
            totalRevenue = BigDecimal("100.0000"),
            nodeCount = 4
        )

        val results = rule.calculate(context)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `multiple purchases generate separate line items`() {
        val context = CalculationContext(
            tenantId = "tenant1",
            settlement = createSettlement(),
            ratesByDepth = mapOf(1 to BigDecimal("1.0")),
            treeMap = createTreeMap(),
            purchases = listOf(
                createPurchase(1, "B", BigDecimal("100.0000")),
                createPurchase(2, "C", BigDecimal("50.0000"))
            ),
            totalRevenue = BigDecimal("150.0000"),
            nodeCount = 4
        )

        val results = rule.calculate(context)

        // Purchase 1 (B): A gets 1% = 1.0000
        // Purchase 2 (C): B gets 1% = 0.5000
        assertEquals(2, results.size)

        val fromPurchase1 = results.find { it.sourcePurchaseId == 1L }!!
        assertEquals("A", fromPurchase1.recipientCustomerId)
        assertEquals(BigDecimal("1.0000"), fromPurchase1.amount)

        val fromPurchase2 = results.find { it.sourcePurchaseId == 2L }!!
        assertEquals("B", fromPurchase2.recipientCustomerId)
        assertEquals(BigDecimal("0.5000"), fromPurchase2.amount)
    }

    @Test
    fun `rounding uses HALF_UP with 4 decimal places`() {
        val context = CalculationContext(
            tenantId = "tenant1",
            settlement = createSettlement(),
            ratesByDepth = mapOf(1 to BigDecimal("3.0")),
            treeMap = createTreeMap(),
            purchases = listOf(createPurchase(1, "B", BigDecimal("33.3333"))),
            totalRevenue = BigDecimal("33.3333"),
            nodeCount = 4
        )

        val results = rule.calculate(context)

        assertEquals(1, results.size)
        // 33.3333 * 3.0 / 100 = 0.999999 -> rounded to 1.0000
        assertEquals(BigDecimal("1.0000"), results[0].amount)
    }
}
