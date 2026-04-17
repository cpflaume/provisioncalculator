package com.provisions.calculator.repository

import com.provisions.calculator.model.UserTenant
import com.provisions.calculator.model.UserTenantId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface UserTenantRepository : JpaRepository<UserTenant, UserTenantId> {

    @Query("SELECT ut.id.tenantId FROM UserTenant ut WHERE ut.id.userId = :userId")
    fun findTenantIdsByUserId(userId: Long): List<String>

    @Modifying
    @Query("DELETE FROM UserTenant ut WHERE ut.id.userId = :userId AND ut.id.tenantId = :tenantId")
    fun deleteByUserIdAndTenantId(userId: Long, tenantId: String)

    fun existsByIdUserIdAndIdTenantId(userId: Long, tenantId: String): Boolean
}
