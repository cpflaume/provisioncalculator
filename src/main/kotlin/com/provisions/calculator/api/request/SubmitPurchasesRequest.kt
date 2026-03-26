package com.provisions.calculator.api.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.LocalDateTime

data class PurchaseRequest(
    @field:NotBlank
    @Schema(description = "Customer ID of the buyer (must exist in the referral tree)", example = "diana")
    val buyerCustomerId: String,
    @field:Positive
    @Schema(description = "Purchase amount", example = "1000.00")
    val amount: BigDecimal,
    @Schema(description = "When the purchase occurred (ISO 8601)", example = "2026-03-15T14:30:00")
    val purchasedAt: LocalDateTime
)

data class SubmitPurchasesRequest(
    @field:Valid
    @field:NotEmpty
    val purchases: List<PurchaseRequest>
)
