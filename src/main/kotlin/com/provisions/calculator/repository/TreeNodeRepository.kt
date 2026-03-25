package com.provisions.calculator.repository

import com.provisions.calculator.model.TreeNode
import org.springframework.data.jpa.repository.JpaRepository

interface TreeNodeRepository : JpaRepository<TreeNode, Long> {
    fun findByTenantIdAndSettlementId(tenantId: String, settlementId: Long): List<TreeNode>
    fun deleteByTenantIdAndSettlementId(tenantId: String, settlementId: Long)
}
