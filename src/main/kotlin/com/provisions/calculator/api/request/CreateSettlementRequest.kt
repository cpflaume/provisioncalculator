package com.provisions.calculator.api.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateSettlementRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val name: String
)
