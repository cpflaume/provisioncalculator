package com.provisions.calculator.service

import com.provisions.calculator.model.Tenant
import com.provisions.calculator.model.User
import com.provisions.calculator.model.UserRole
import com.provisions.calculator.model.UserStatus
import com.provisions.calculator.model.UserTenant
import com.provisions.calculator.model.UserTenantId
import com.provisions.calculator.repository.TenantRepository
import com.provisions.calculator.repository.UserRepository
import com.provisions.calculator.repository.UserTenantRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

data class UserAdminView(
    val userId: Long,
    val email: String,
    val displayName: String,
    val role: UserRole,
    val status: UserStatus,
    val tenantIds: List<String>
)

data class TenantView(
    val id: String,
    val name: String
)

@Service
class UserAdminService(
    private val userRepository: UserRepository,
    private val tenantRepository: TenantRepository,
    private val userTenantRepository: UserTenantRepository
) {

    fun listUsers(): List<UserAdminView> {
        val users = userRepository.findAll()
        if (users.isEmpty()) return emptyList()
        val tenantIdsByUser = userTenantRepository.findAllUserTenantMappings()
            .groupBy({ it[0] as Long }, { it[1] as String })
        return users.map { it.toAdminView(tenantIdsByUser) }
    }

    @Transactional
    fun activate(userId: Long): UserAdminView {
        val user = findUser(userId)
        user.status = UserStatus.ACTIVE
        return userRepository.save(user).toAdminView()
    }

    @Transactional
    fun disable(userId: Long): UserAdminView {
        val user = findUser(userId)
        user.status = UserStatus.DISABLED
        return userRepository.save(user).toAdminView()
    }

    fun listTenants(): List<TenantView> = tenantRepository.findAll().map { TenantView(it.id, it.name) }

    @Transactional
    fun createTenant(id: String, name: String): TenantView {
        val tenant = tenantRepository.save(Tenant(id = id, name = name))
        return TenantView(tenant.id, tenant.name)
    }

    @Transactional
    fun assignTenant(userId: Long, tenantId: String) {
        val user = findUser(userId)
        val tenant = tenantRepository.findById(tenantId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found") }
        if (!userTenantRepository.existsByIdUserIdAndIdTenantId(userId, tenantId)) {
            userTenantRepository.save(UserTenant(id = UserTenantId(user.id, tenant.id), user = user, tenant = tenant))
        }
    }

    @Transactional
    fun revokeTenant(userId: Long, tenantId: String) {
        userTenantRepository.deleteByUserIdAndTenantId(userId, tenantId)
    }

    private fun findUser(userId: Long) = userRepository.findById(userId)
        .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }

    private fun User.toAdminView(tenantIdsByUser: Map<Long, List<String>> = emptyMap()) = UserAdminView(
        userId = id,
        email = email,
        displayName = displayName,
        role = role,
        status = status,
        tenantIds = tenantIdsByUser[id] ?: userTenantRepository.findTenantIdsByUserId(id)
    )
}
