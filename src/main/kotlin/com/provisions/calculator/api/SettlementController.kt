package com.provisions.calculator.api

import com.provisions.calculator.api.request.ConfigureSettingsRequest
import com.provisions.calculator.api.request.CreateSettlementRequest
import com.provisions.calculator.model.SettlementStatus
import com.provisions.calculator.service.SettlementService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/settlements")
class SettlementController(private val settlementService: SettlementService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable tenantId: String,
        @Valid @RequestBody request: CreateSettlementRequest
    ): SettlementResponse {
        val settlement = settlementService.create(tenantId, request)
        return SettlementResponse(settlement.id, settlement.tenantId, settlement.name, settlement.status, settlement.createdAt)
    }

    @GetMapping("/{settlementId}")
    fun findById(
        @PathVariable tenantId: String,
        @PathVariable settlementId: Long
    ): SettlementResponse {
        val settlement = settlementService.findById(tenantId, settlementId)
        return SettlementResponse(settlement.id, settlement.tenantId, settlement.name, settlement.status, settlement.createdAt)
    }

    @GetMapping
    fun findAll(
        @PathVariable tenantId: String,
        @RequestParam(required = false) status: SettlementStatus?
    ): List<SettlementResponse> {
        return settlementService.findAll(tenantId, status).map {
            SettlementResponse(it.id, it.tenantId, it.name, it.status, it.createdAt)
        }
    }

    @PutMapping("/{settlementId}/config")
    fun configure(
        @PathVariable tenantId: String,
        @PathVariable settlementId: Long,
        @Valid @RequestBody request: ConfigureSettingsRequest
    ): ConfigResponse {
        val settlement = settlementService.configure(tenantId, settlementId, request)
        val config = settlementService.getConfig(tenantId, settlementId)
        return ConfigResponse(
            settlementId = settlement.id,
            rates = config.rates.map { RateResponse(it.depth, it.ratePercent) },
            nodeCount = config.nodes.size,
            updatedAt = LocalDateTime.now()
        )
    }

    @GetMapping("/{settlementId}/config")
    fun getConfig(
        @PathVariable tenantId: String,
        @PathVariable settlementId: Long
    ): GetConfigResponse {
        val config = settlementService.getConfig(tenantId, settlementId)
        return GetConfigResponse(
            settlementId = config.settlement.id,
            rates = config.rates.map { RateResponse(it.depth, it.ratePercent) },
            tree = config.nodes.map { TreeNodeResponse(it.customerId, config.parentCustomerIdMap[it.customerId]) },
            nodeCount = config.nodes.size,
            updatedAt = LocalDateTime.now()
        )
    }

    @PostMapping("/{settlementId}/approve")
    fun approve(
        @PathVariable tenantId: String,
        @PathVariable settlementId: Long
    ): StatusResponse {
        val settlement = settlementService.approve(tenantId, settlementId)
        return StatusResponse(settlement.id, settlement.status.name)
    }

    @PostMapping("/{settlementId}/reject")
    fun reject(
        @PathVariable tenantId: String,
        @PathVariable settlementId: Long
    ): StatusResponse {
        val settlement = settlementService.reject(tenantId, settlementId)
        return StatusResponse(settlement.id, settlement.status.name)
    }
}

data class SettlementResponse(val id: Long, val tenantId: String, val name: String, val status: SettlementStatus, val createdAt: LocalDateTime)
data class ConfigResponse(val settlementId: Long, val rates: List<RateResponse>, val nodeCount: Int, val updatedAt: LocalDateTime)
data class GetConfigResponse(val settlementId: Long, val rates: List<RateResponse>, val tree: List<TreeNodeResponse>, val nodeCount: Int, val updatedAt: LocalDateTime)
data class RateResponse(val depth: Int, val ratePercent: java.math.BigDecimal)
data class TreeNodeResponse(val customerId: String, val parentCustomerId: String?)
data class StatusResponse(val settlementId: Long, val status: String)
