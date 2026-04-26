package com.provisions.calculator.api.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal

data class CommissionRateRequest(
    @field:Positive
    @Schema(description = "Tree depth level (1 = parent, 2 = grandparent, ...)", example = "1")
    val depth: Int,
    @field:PositiveOrZero
    @field:Digits(integer = 4, fraction = 4, message = "Rate must have at most 4 integer and 4 fractional digits")
    @Schema(description = "Commission percentage applied to purchase amount", example = "10.0")
    val ratePercent: BigDecimal
)

@Schema(description = "Configuration for commission rates and referral tree. Replaces any existing config. Rates and tree may be saved independently — either list may be empty.")
data class ConfigureSettingsRequest(
    @field:Valid
    val rates: List<CommissionRateRequest> = emptyList(),

    @field:Valid
    val tree: List<TreeNodeRequest> = emptyList()
)
