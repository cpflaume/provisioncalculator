package com.provisions.calculator.api.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateSettlementRequest(
    @field:NotBlank
    @field:Size(max = 255)
    @Schema(description = "Name of the settlement period", example = "March 2026")
    val name: String
)
