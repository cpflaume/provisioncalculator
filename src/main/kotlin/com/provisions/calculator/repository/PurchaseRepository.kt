package com.provisions.calculator.repository

import com.provisions.calculator.model.Purchase
import com.provisions.calculator.model.SettlementStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal

interface PurchaseRepository : JpaRepository<Purchase, Long> {
    fun findByTenantIdAndSettlementId(tenantId: String, settlementId: Long): List<Purchase>
    fun findByTenantIdAndSettlementId(tenantId: String, settlementId: Long, pageable: Pageable): Page<Purchase>

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Purchase p WHERE p.tenantId = :tenantId")
    fun sumAmountByTenantId(@Param("tenantId") tenantId: String): BigDecimal

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Purchase p WHERE p.tenantId = :tenantId AND p.settlement.status = :status")
    fun sumAmountByTenantIdAndSettlementStatus(@Param("tenantId") tenantId: String, @Param("status") status: SettlementStatus): BigDecimal
}
