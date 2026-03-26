package com.provisions.calculator.repository

import com.provisions.calculator.model.CommissionResult
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface CommissionResultRepository : JpaRepository<CommissionResult, Long> {
    @EntityGraph(attributePaths = ["sourcePurchase"])
    fun findByCalculationId(calculationId: UUID): List<CommissionResult>

    @EntityGraph(attributePaths = ["sourcePurchase"])
    fun findByCalculationIdAndRecipientCustomerId(calculationId: UUID, recipientCustomerId: String): List<CommissionResult>
}
