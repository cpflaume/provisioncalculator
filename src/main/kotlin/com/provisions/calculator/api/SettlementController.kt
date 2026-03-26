package com.provisions.calculator.api

import com.provisions.calculator.api.request.ConfigureSettingsRequest
import com.provisions.calculator.api.request.CreateSettlementRequest
import com.provisions.calculator.model.SettlementStatus
import com.provisions.calculator.service.SettlementService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/settlements")
@Tag(name = "Settlements")
class SettlementController(private val settlementService: SettlementService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create a new settlement",
        description = "Creates a new settlement period (e.g. a month) in OPEN status. All purchases and commissions belong to a settlement."
    )
    fun create(
        @Parameter(description = "Tenant identifier", example = "acme") @PathVariable tenantId: String,
        @Valid @RequestBody request: CreateSettlementRequest
    ): SettlementResponse {
        val settlement = settlementService.create(tenantId, request)
        return SettlementResponse(settlement.id, settlement.tenantId, settlement.name, settlement.status, settlement.createdAt)
    }

    @GetMapping("/{settlementId}")
    @Operation(summary = "Get settlement by ID")
    fun findById(
        @Parameter(description = "Tenant identifier", example = "acme") @PathVariable tenantId: String,
        @PathVariable settlementId: Long
    ): SettlementResponse {
        val settlement = settlementService.findById(tenantId, settlementId)
        return SettlementResponse(settlement.id, settlement.tenantId, settlement.name, settlement.status, settlement.createdAt)
    }

    @GetMapping
    @Operation(
        summary = "List all settlements",
        description = "Returns all settlements for the tenant. Optionally filter by status."
    )
    fun findAll(
        @Parameter(description = "Tenant identifier", example = "acme") @PathVariable tenantId: String,
        @Parameter(description = "Filter by status", example = "OPEN") @RequestParam(required = false) status: SettlementStatus?
    ): List<SettlementResponse> {
        return settlementService.findAll(tenantId, status).map {
            SettlementResponse(it.id, it.tenantId, it.name, it.status, it.createdAt)
        }
    }

    @PutMapping("/{settlementId}/config")
    @Operation(
        summary = "Configure commission rates and referral tree",
        description = """Upload the referral tree and commission rates for this settlement. This replaces any previous configuration.

**Tree rules:** Exactly one root node (parentCustomerId = null), no duplicates, no orphans, no cycles.

**Rates:** depth 1 = direct upline (parent), depth 2 = grandparent, etc. The ratePercent is applied to each purchase amount."""
    )
    fun configure(
        @Parameter(description = "Tenant identifier", example = "acme") @PathVariable tenantId: String,
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
    @Operation(summary = "Get current configuration", description = "Returns the current commission rates and referral tree for this settlement.")
    fun getConfig(
        @Parameter(description = "Tenant identifier", example = "acme") @PathVariable tenantId: String,
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
    @Operation(
        summary = "Approve settlement",
        description = "Transitions from CALCULATED to APPROVED. Once approved, no further modifications are allowed."
    )
    fun approve(
        @Parameter(description = "Tenant identifier", example = "acme") @PathVariable tenantId: String,
        @PathVariable settlementId: Long
    ): StatusResponse {
        val settlement = settlementService.approve(tenantId, settlementId)
        return StatusResponse(settlement.id, settlement.status.name)
    }

    @PostMapping("/{settlementId}/reject")
    @Operation(
        summary = "Reject settlement",
        description = "Transitions from CALCULATED back to OPEN. Allows modifications to purchases and configuration before recalculating."
    )
    fun reject(
        @Parameter(description = "Tenant identifier", example = "acme") @PathVariable tenantId: String,
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
