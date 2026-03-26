package com.provisions.calculator.api.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class TreeNodeRequest(
    @field:NotBlank
    @Schema(description = "Unique customer identifier in the tree", example = "alice")
    val customerId: String,
    @Schema(description = "Customer ID of the parent node (null for root)", example = "null")
    val parentCustomerId: String? = null
)
