package com.provisions.calculator.api.request

import jakarta.validation.constraints.NotBlank

data class TreeNodeRequest(
    @field:NotBlank
    val customerId: String,
    val parentCustomerId: String? = null
)
