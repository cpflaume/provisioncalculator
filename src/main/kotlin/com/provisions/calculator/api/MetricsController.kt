package com.provisions.calculator.api

import com.provisions.calculator.service.MetricsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
@Tag(name = "Metrics")
class MetricsController(private val metricsService: MetricsService) {

    @GetMapping("/metrics/overview")
    @Operation(
        summary = "Get tenant-level KPI overview",
        description = """Returns aggregated KPIs across all settlements for the tenant:
settlements by status, total purchase volume, total commission, and average commission rate."""
    )
    fun getTenantOverview(
        @Parameter(description = "Tenant identifier", example = "acme") @PathVariable tenantId: String
    ): MetricsService.TenantOverview {
        return metricsService.getTenantOverview(tenantId)
    }

    @GetMapping("/settlements/{settlementId}/metrics")
    @Operation(
        summary = "Get settlement analysis metrics",
        description = """Returns detailed analysis for a single settlement:
purchase statistics with outlier detection, commission distribution by depth with outliers,
and a cross-check comparing actual commission against theoretical maximum."""
    )
    fun getSettlementMetrics(
        @Parameter(description = "Tenant identifier", example = "acme") @PathVariable tenantId: String,
        @PathVariable settlementId: Long
    ): MetricsService.SettlementMetrics {
        return metricsService.getSettlementMetrics(tenantId, settlementId)
    }
}
