package com.provisions.calculator.api.request

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.LocalDateTime

data class PurchaseRequest(
    @field:NotBlank
    val buyerCustomerId: String,
    @field:Positive
    val amount: BigDecimal,
    val purchasedAt: LocalDateTime
)

data class SubmitPurchasesRequest(
    @field:Valid
    @field:NotEmpty
    val purchases: List<PurchaseRequest>
)
