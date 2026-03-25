package com.provisions.calculator.api.request

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import java.math.BigDecimal
import java.time.LocalDateTime

data class PurchaseRequest(
    val buyerCustomerId: String,
    val amount: BigDecimal,
    val purchasedAt: LocalDateTime
)

data class SubmitPurchasesRequest(
    @field:Valid
    @field:NotEmpty
    val purchases: List<PurchaseRequest>
)
