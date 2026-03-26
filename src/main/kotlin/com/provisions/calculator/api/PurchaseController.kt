package com.provisions.calculator.api

import com.provisions.calculator.api.request.SubmitPurchasesRequest
import com.provisions.calculator.service.PurchaseService
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/settlements/{settlementId}/purchases")
class PurchaseController(private val purchaseService: PurchaseService) {

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun submitBatch(
        @PathVariable tenantId: String,
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
    fun findAll(
        @PathVariable tenantId: String,
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
