package com.provisions.calculator.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "tenants")
class Tenant(
    @Id
    val id: String,

    @Column(nullable = false)
    val name: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
