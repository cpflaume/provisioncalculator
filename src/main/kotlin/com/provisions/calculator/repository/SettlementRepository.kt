package com.provisions.calculator.repository

import com.provisions.calculator.model.Settlement
import com.provisions.calculator.model.SettlementStatus
import org.springframework.data.jpa.repository.JpaRepository

interface SettlementRepository : JpaRepository<Settlement, Long> {
    fun findByTenantIdAndId(tenantId: String, id: Long): Settlement?
    fun findByTenantIdAndStatus(tenantId: String, status: SettlementStatus): List<Settlement>
    fun findByTenantId(tenantId: String): List<Settlement>
}
