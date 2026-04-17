package com.provisions.calculator.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "users")
class User(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true)
    val email: String,

    @Column(name = "password_hash")
    var passwordHash: String? = null,

    @Column(name = "password_salt")
    var passwordSalt: String? = null,

    @Column(name = "display_name", nullable = false)
    val displayName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: UserRole = UserRole.USER,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: UserStatus = UserStatus.PENDING,

    @Column(name = "auth_provider", nullable = false)
    val authProvider: String = "LOCAL",

    @Column(name = "provider_id")
    val providerId: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
