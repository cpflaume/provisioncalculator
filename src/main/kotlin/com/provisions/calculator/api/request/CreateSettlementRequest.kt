package com.provisions.calculator.api.request

import jakarta.validation.constraints.NotBlank

data class CreateSettlementRequest(
    @field:NotBlank
    val name: String
)
