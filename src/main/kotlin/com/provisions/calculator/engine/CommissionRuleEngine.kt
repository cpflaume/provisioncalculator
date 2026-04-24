package com.provisions.calculator.engine

import org.springframework.stereotype.Component

@Component
class CommissionRuleEngine(rules: List<CommissionRule>) {

    private val sortedRules = rules.sortedBy { it.order }

    fun execute(context: CalculationContext): List<CommissionLineItem> {
        return sortedRules
            .filter { it.isApplicable(context) }
            .flatMap { it.calculate(context) }
    }
}
