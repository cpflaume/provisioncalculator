package com.provisions.calculator.security

import com.provisions.calculator.model.UserRole
import com.provisions.calculator.model.UserStatus
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

class AppUserDetails(
    val userId: Long,
    private val email: String,
    private val hashedPassword: String?,
    val role: UserRole,
    val status: UserStatus,
    val tenantIds: Set<String>
) : UserDetails {

    override fun getAuthorities(): Collection<GrantedAuthority> =
        listOf(SimpleGrantedAuthority("ROLE_${role.name}"))

    override fun getPassword(): String? = hashedPassword
    override fun getUsername(): String = email
    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = status != UserStatus.DISABLED
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = status == UserStatus.ACTIVE
}
