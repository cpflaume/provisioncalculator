package com.provisions.calculator.engine

import org.springframework.stereotype.Component

@Component
class CommissionRuleEngine(private val rules: List<CommissionRule>) {

    fun execute(context: CalculationContext): List<CommissionLineItem> {
        return rules
            .sortedBy { it.order }
            .filter { it.isApplicable(context) }
            .flatMap { it.calculate(context) }
    }
}
