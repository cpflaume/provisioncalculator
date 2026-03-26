package com.provisions.calculator.model

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "commission_rate", uniqueConstraints = [UniqueConstraint(columnNames = ["settings_id", "depth"])])
class CommissionRate(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settings_id", nullable = false)
    val settings: CommissionSettings,

    @Column(nullable = false)
    val depth: Int,

    @Column(name = "rate_percent", nullable = false, precision = 8, scale = 4)
    val ratePercent: BigDecimal
)
