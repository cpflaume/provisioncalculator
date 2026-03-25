package com.provisions.calculator.engine

import java.math.BigDecimal

data class CommissionLineItem(
    val recipientCustomerId: String,
    val sourcePurchaseId: Long?,
    val amount: BigDecimal,
    val depth: Int?,
    val ruleId: String
)
