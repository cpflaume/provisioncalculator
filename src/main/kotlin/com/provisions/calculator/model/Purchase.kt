package com.provisions.calculator.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "purchase")
class Purchase(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id", nullable = false)
    val settlement: Settlement,

    @Column(name = "buyer_customer_id", nullable = false)
    val buyerCustomerId: String,

    @Column(nullable = false, precision = 15, scale = 4)
    val amount: BigDecimal,

    @Column(name = "purchased_at", nullable = false)
    val purchasedAt: LocalDateTime
)
