package com.provisions.calculator.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "commission_settings", uniqueConstraints = [UniqueConstraint(columnNames = ["tenant_id", "settlement_id"])])
class CommissionSettings(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id", nullable = false)
    val settlement: Settlement,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),

    @OneToMany(mappedBy = "settings", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val rates: MutableList<CommissionRate> = mutableListOf()
)
