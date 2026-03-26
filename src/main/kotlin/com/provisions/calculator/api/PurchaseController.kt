package com.provisions.calculator.api

import com.provisions.calculator.api.request.SubmitPurchasesRequest
import com.provisions.calculator.service.PurchaseService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/settlements/{settlementId}/purchases")
@Tag(name = "Purchases")
class PurchaseController(private val purchaseService: PurchaseService) {

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        summary = "Submit a batch of purchases",
        description = """Submit one or more purchases made by customers in the referral tree.
You can call this endpoint multiple times — purchases accumulate within the settlement.

If the settlement was previously CALCULATED, submitting new purchases resets it to OPEN."""
    )
    fun submitBatch(
        @Parameter(description = "Tenant identifier", example = "acme") @PathVariable tenantId: String,
        @PathVariable settlementId: Long,
        @Valid @RequestBody request: SubmitPurchasesRequest
    ): PurchaseSubmitResponse {
        val purchases = purchaseService.submitBatch(tenantId, settlementId, request)
        return PurchaseSubmitResponse(
            settlementId = settlementId,
            accepted = purchases.size,
            submittedAt = LocalDateTime.now()
        )
    }

    @GetMapping
    @Operation(
        summary = "List purchases",
        description = "Returns all purchases for this settlement, paginated. Use query params `page` (0-based) and `size` (default 20)."
    )
    fun findAll(
        @Parameter(description = "Tenant identifier", example = "acme") @PathVariable tenantId: String,
        @PathVariable settlementId: Long,
        pageable: Pageable
    ): PurchasePageResponse {
        val page = purchaseService.findAll(tenantId, settlementId, pageable)
        return PurchasePageResponse(
            content = page.content.map { PurchaseResponse(it.id, it.buyerCustomerId, it.amount, it.purchasedAt) },
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            page = page.number,
            size = page.size
        )
    }
}

data class PurchaseSubmitResponse(val settlementId: Long, val accepted: Int, val submittedAt: LocalDateTime)
data class PurchaseResponse(val id: Long, val buyerCustomerId: String, val amount: BigDecimal, val purchasedAt: LocalDateTime)
data class PurchasePageResponse(val content: List<PurchaseResponse>, val totalElements: Long, val totalPages: Int, val page: Int, val size: Int)
