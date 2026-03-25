package com.provisions.calculator.api.request

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import java.math.BigDecimal

data class CommissionRateRequest(
    val depth: Int,
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
