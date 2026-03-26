package com.provisions.calculator.model

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "commission_result")
class CommissionResult(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id", nullable = false)
    val settlement: Settlement,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calculation_id", nullable = false)
    val calculation: Calculation,

    @Column(name = "recipient_customer_id", nullable = false)
    val recipientCustomerId: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_purchase_id")
    val sourcePurchase: Purchase? = null,

    @Column(nullable = false, precision = 15, scale = 4)
    val amount: BigDecimal,

    val depth: Int? = null,

    @Column(name = "rule_id", nullable = false)
    val ruleId: String
)
