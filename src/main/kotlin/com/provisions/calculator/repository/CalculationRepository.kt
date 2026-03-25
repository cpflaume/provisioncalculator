package com.provisions.calculator.repository

import com.provisions.calculator.model.Calculation
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface CalculationRepository : JpaRepository<Calculation, UUID> {
    fun findByTenantIdAndSettlementIdAndInputHash(tenantId: String, settlementId: Long, inputHash: String): Calculation?
    fun findFirstByTenantIdAndSettlementIdOrderByCalculatedAtDesc(tenantId: String, settlementId: Long): Calculation?
}
