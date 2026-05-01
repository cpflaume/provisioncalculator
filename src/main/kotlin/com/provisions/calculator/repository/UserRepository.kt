package com.provisions.calculator.repository

import com.provisions.calculator.model.User
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.Optional

interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): Optional<User>
    fun existsByEmail(email: String): Boolean
    fun findAllByAuthProviderAndExpiresAtBefore(authProvider: String, cutoff: Instant): List<User>
}
