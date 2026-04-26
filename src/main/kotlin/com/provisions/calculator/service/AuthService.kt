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
import com.provisions.calculator.security.JwtService
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

data class AuthUserResponse(
    val userId: Long,
    val email: String,
    val displayName: String,
    val role: UserRole,
    val status: UserStatus,
    val tenantIds: Set<String>
)

data class AuthResponse(
    val token: String,
    val user: AuthUserResponse
)

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val tenantRepository: TenantRepository,
    private val userTenantRepository: UserTenantRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val slugService: SlugService
) {

    @Transactional
    fun register(email: String, password: String, displayName: String): AuthResponse {
        if (userRepository.existsByEmail(email)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Email already registered")
        }

        val user = userRepository.save(
            User(
                email = email,
                passwordHash = passwordEncoder.encode(password),
                displayName = displayName,
                role = UserRole.USER,
                status = UserStatus.PENDING
            )
        )

        val tenantSlug = slugService.slugify(displayName)
        val tenant = tenantRepository.save(Tenant(id = tenantSlug, name = displayName))
        userTenantRepository.save(UserTenant(id = UserTenantId(user.id, tenant.id), user = user, tenant = tenant))

        val tenantIds = setOf(tenant.id)
        val token = jwtService.generate(user.id, user.email, user.displayName, user.role, user.status, tenantIds)
        return AuthResponse(token, user.toResponse(tenantIds))
    }

    fun login(email: String, password: String): AuthResponse {
        val user = userRepository.findByEmail(email)
            .orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials") }

        if (user.passwordHash == null || !passwordEncoder.matches(password, user.passwordHash)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")
        }

        val tenantIds = userTenantRepository.findTenantIdsByUserId(user.id).toSet()
        val token = jwtService.generate(user.id, user.email, user.displayName, user.role, user.status, tenantIds)
        return AuthResponse(token, user.toResponse(tenantIds))
    }

    private fun User.toResponse(tenantIds: Set<String>) = AuthUserResponse(
        userId = id,
        email = email,
        displayName = displayName,
        role = role,
        status = status,
        tenantIds = tenantIds
    )
}
