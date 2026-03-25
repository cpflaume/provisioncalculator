package com.provisions.calculator.repository

import com.provisions.calculator.model.CommissionResult
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface CommissionResultRepository : JpaRepository<CommissionResult, Long> {
    fun findByCalculationId(calculationId: UUID): List<CommissionResult>
    fun findByCalculationIdAndRecipientCustomerId(calculationId: UUID, recipientCustomerId: String): List<CommissionResult>
}
