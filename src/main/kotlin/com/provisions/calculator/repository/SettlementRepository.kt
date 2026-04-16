package com.provisions.calculator.repository

import com.provisions.calculator.model.Settlement
import com.provisions.calculator.model.SettlementStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SettlementRepository : JpaRepository<Settlement, Long> {
    fun findByTenantIdAndId(tenantId: String, id: Long): Settlement?
    fun findByTenantIdAndStatus(tenantId: String, status: SettlementStatus): List<Settlement>
    fun findByTenantId(tenantId: String): List<Settlement>

    @Query("SELECT s.status, COUNT(s) FROM Settlement s WHERE s.tenantId = :tenantId GROUP BY s.status")
    fun countByStatusForTenant(@Param("tenantId") tenantId: String): List<Array<Any>>
}
