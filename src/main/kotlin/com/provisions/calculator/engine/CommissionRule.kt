package com.provisions.calculator.engine

interface CommissionRule {
    val ruleId: String
    val order: Int get() = 100
    fun isApplicable(context: CalculationContext): Boolean = true
    fun calculate(context: CalculationContext): List<CommissionLineItem>
}
