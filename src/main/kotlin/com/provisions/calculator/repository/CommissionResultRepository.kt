package com.provisions.calculator.repository

import com.provisions.calculator.model.CommissionResult
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.util.*

interface CommissionResultRepository : JpaRepository<CommissionResult, Long> {
    @EntityGraph(attributePaths = ["sourcePurchase"])
    fun findByCalculationId(calculationId: UUID): List<CommissionResult>

    @EntityGraph(attributePaths = ["sourcePurchase"])
    fun findByCalculationIdAndRecipientCustomerId(calculationId: UUID, recipientCustomerId: String): List<CommissionResult>

    @Query(
        value = """
            SELECT COALESCE(SUM(cr.amount), 0)
            FROM commission_result cr
            WHERE cr.tenant_id = :tenantId
            AND cr.calculation_id IN (
                SELECT DISTINCT ON (c.settlement_id) c.id
                FROM calculation c
                WHERE c.tenant_id = :tenantId
                ORDER BY c.settlement_id, c.calculated_at DESC
            )
        """,
        nativeQuery = true
    )
    fun sumAmountForLatestCalculationsByTenantId(@Param("tenantId") tenantId: String): BigDecimal
}
