package com.provisions.calculator.api.request

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal

data class CommissionRateRequest(
    @field:Positive
    val depth: Int,
    @field:PositiveOrZero
    val ratePercent: BigDecimal
)

data class ConfigureSettingsRequest(
    @field:Valid
    @field:NotEmpty
    val rates: List<CommissionRateRequest>,

    @field:Valid
    @field:NotEmpty
    val tree: List<TreeNodeRequest>
)
