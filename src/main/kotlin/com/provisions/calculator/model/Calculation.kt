package com.provisions.calculator.model

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "calculation", uniqueConstraints = [UniqueConstraint(columnNames = ["tenant_id", "settlement_id", "input_hash"])])
class Calculation(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id", nullable = false)
    val settlement: Settlement,

    @Column(name = "input_hash", nullable = false, length = 64)
    val inputHash: String,

    @Column(name = "calculated_at", nullable = false)
    val calculatedAt: LocalDateTime = LocalDateTime.now()
)
