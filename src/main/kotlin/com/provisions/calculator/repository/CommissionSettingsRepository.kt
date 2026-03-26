package com.provisions.calculator.repository

import com.provisions.calculator.model.CommissionSettings
import org.springframework.data.jpa.repository.JpaRepository

interface CommissionSettingsRepository : JpaRepository<CommissionSettings, Long> {
    fun findByTenantIdAndSettlementId(tenantId: String, settlementId: Long): CommissionSettings?
}
