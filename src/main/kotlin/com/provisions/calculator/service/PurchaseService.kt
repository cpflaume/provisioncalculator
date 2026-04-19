package com.provisions.calculator.service

import com.provisions.calculator.api.request.SubmitPurchasesRequest
import com.provisions.calculator.model.Purchase
import com.provisions.calculator.model.SettlementStatus
import com.provisions.calculator.repository.PurchaseRepository
import com.provisions.calculator.repository.SettlementRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class PurchaseService(
    private val purchaseRepository: PurchaseRepository,
    private val settlementService: SettlementService,
    private val settlementRepository: SettlementRepository
) {

    @Transactional
    fun submitBatch(tenantId: String, settlementId: Long, request: SubmitPurchasesRequest): List<Purchase> {
        val settlement = settlementService.findById(tenantId, settlementId)
        settlementService.guardNotApproved(settlement)

        if (request.purchases.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Purchases list cannot be empty")
        }

        val purchases = request.purchases.map { p ->
            Purchase(
                tenantId = tenantId,
                settlement = settlement,
                buyerCustomerId = p.buyerCustomerId,
                amount = p.amount,
                purchasedAt = p.purchasedAt
            )
        }

        val saved = purchaseRepository.saveAll(purchases)

        // Reset status to OPEN if was CALCULATED
        if (settlement.status == SettlementStatus.CALCULATED) {
            settlement.status = SettlementStatus.OPEN
            settlementRepository.save(settlement)
        }

        return saved
    }

    fun findAll(tenantId: String, settlementId: Long, pageable: Pageable): Page<Purchase> {
        settlementService.findById(tenantId, settlementId)
        return purchaseRepository.findByTenantIdAndSettlementId(tenantId, settlementId, pageable)
    }

    @Transactional
    fun deletePurchase(tenantId: String, settlementId: Long, purchaseId: Long) {
        val settlement = settlementService.findById(tenantId, settlementId)
        settlementService.guardNotApproved(settlement)

        val purchase = purchaseRepository.findById(purchaseId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase $purchaseId not found")
        }

        if (purchase.tenantId != tenantId || purchase.settlement.id != settlementId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase $purchaseId not found")
        }

        purchaseRepository.delete(purchase)
    }
}
