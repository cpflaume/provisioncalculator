package com.provisions.calculator.model

import jakarta.persistence.*
import java.time.Instant

enum class SettlementStatus {
    OPEN, CALCULATED, APPROVED, REJECTED
}

@Entity
@Table(name = "settlement")
class Settlement(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String,

    @Column(nullable = false)
    val name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: SettlementStatus = SettlementStatus.OPEN,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
