package com.provisions.calculator.repository

import com.provisions.calculator.model.TreeNode
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface TreeNodeRepository : JpaRepository<TreeNode, Long> {
    fun findByTenantIdAndSettlementId(tenantId: String, settlementId: Long): List<TreeNode>

    @Query("SELECT t FROM TreeNode t LEFT JOIN FETCH t.parentNode WHERE t.tenantId = :tenantId AND t.settlement.id = :settlementId")
    fun findWithParentByTenantIdAndSettlementId(tenantId: String, settlementId: Long): List<TreeNode>

    @Modifying
    @Query("DELETE FROM TreeNode t WHERE t.tenantId = :tenantId AND t.settlement.id = :settlementId")
    fun deleteByTenantIdAndSettlementId(tenantId: String, settlementId: Long)
}
