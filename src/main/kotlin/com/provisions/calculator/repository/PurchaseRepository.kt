package com.provisions.calculator.repository

import com.provisions.calculator.model.Purchase
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface PurchaseRepository : JpaRepository<Purchase, Long> {
    fun findByTenantIdAndSettlementId(tenantId: String, settlementId: Long): List<Purchase>
    fun findByTenantIdAndSettlementId(tenantId: String, settlementId: Long, pageable: Pageable): Page<Purchase>
}
