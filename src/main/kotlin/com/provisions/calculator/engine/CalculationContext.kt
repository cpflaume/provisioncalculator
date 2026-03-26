package com.provisions.calculator.engine

import com.provisions.calculator.model.Purchase
import com.provisions.calculator.model.Settlement
import java.math.BigDecimal

data class TreeNodeMemento(
    val customerId: String,
    val parentCustomerId: String?,
    val children: List<String>
)

data class CalculationContext(
    val tenantId: String,
    val settlement: Settlement,
    val ratesByDepth: Map<Int, BigDecimal>,
    val treeMap: Map<String, TreeNodeMemento>,
    val purchases: List<Purchase>,
    val totalRevenue: BigDecimal,
    val nodeCount: Int
)
