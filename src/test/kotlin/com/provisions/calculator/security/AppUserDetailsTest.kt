package com.provisions.calculator.security

import com.provisions.calculator.model.UserRole
import com.provisions.calculator.model.UserStatus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class AppUserDetailsTest {

    private fun details(
        status: UserStatus = UserStatus.ACTIVE,
        expiresAt: Instant? = null,
        authProvider: String = "LOCAL"
    ) = AppUserDetails(
        userId = 1L,
        email = "user@test.com",
        displayName = "Test",
        hashedPassword = "hash",
        role = UserRole.USER,
        status = status,
        tenantIds = setOf("t1"),
        authProvider = authProvider,
        expiresAt = expiresAt
    )

    // isAccountNonExpired

    @Test
    fun `isAccountNonExpired - returns true when expiresAt is null`() {
        assertTrue(details(expiresAt = null).isAccountNonExpired)
    }

    @Test
    fun `isAccountNonExpired - returns true when expiresAt is in the future`() {
        val future = Instant.now().plus(1, ChronoUnit.HOURS)
        assertTrue(details(expiresAt = future).isAccountNonExpired)
    }

    @Test
    fun `isAccountNonExpired - returns false when expiresAt is in the past`() {
        val past = Instant.now().minus(1, ChronoUnit.SECONDS)
        assertFalse(details(expiresAt = past).isAccountNonExpired)
    }

    @Test
    fun `isAccountNonExpired - returns false when expiresAt is exactly now`() {
        val past = Instant.now().minus(1, ChronoUnit.MILLIS)
        assertFalse(details(expiresAt = past).isAccountNonExpired)
    }

    // isAccountNonLocked

    @Test
    fun `isAccountNonLocked - returns false for DISABLED status`() {
        assertFalse(details(status = UserStatus.DISABLED).isAccountNonLocked)
    }

    @Test
    fun `isAccountNonLocked - returns true for ACTIVE status`() {
        assertTrue(details(status = UserStatus.ACTIVE).isAccountNonLocked)
    }

    @Test
    fun `isAccountNonLocked - returns true for PENDING status`() {
        assertTrue(details(status = UserStatus.PENDING).isAccountNonLocked)
    }

    // isEnabled

    @Test
    fun `isEnabled - returns true only for ACTIVE status`() {
        assertTrue(details(status = UserStatus.ACTIVE).isEnabled)
        assertFalse(details(status = UserStatus.PENDING).isEnabled)
        assertFalse(details(status = UserStatus.DISABLED).isEnabled)
    }

    // demo-specific

    @Test
    fun `demo user with future expiresAt is non-expired`() {
        val demo = details(
            status = UserStatus.ACTIVE,
            expiresAt = Instant.now().plus(24, ChronoUnit.HOURS),
            authProvider = "DEMO"
        )
        assertTrue(demo.isAccountNonExpired)
        assertTrue(demo.isEnabled)
    }

    @Test
    fun `demo user with past expiresAt is expired`() {
        val demo = details(
            status = UserStatus.ACTIVE,
            expiresAt = Instant.now().minus(1, ChronoUnit.SECONDS),
            authProvider = "DEMO"
        )
        assertFalse(demo.isAccountNonExpired)
    }
}
