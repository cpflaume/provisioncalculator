package com.provisions.calculator.api.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal

data class CommissionRateRequest(
    @field:Positive
    @Schema(description = "Tree depth level (1 = parent, 2 = grandparent, ...)", example = "1")
    val depth: Int,
    @field:PositiveOrZero
    @Schema(description = "Commission percentage applied to purchase amount", example = "10.0")
    val ratePercent: BigDecimal
)

@Schema(description = "Configuration for commission rates and referral tree. Replaces any existing config.")
data class ConfigureSettingsRequest(
    @field:Valid
    @field:NotEmpty
    val rates: List<CommissionRateRequest>,

    @field:Valid
    @field:NotEmpty
    val tree: List<TreeNodeRequest>
)
