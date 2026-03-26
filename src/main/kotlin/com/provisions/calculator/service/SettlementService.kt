package com.provisions.calculator.service

import com.provisions.calculator.api.request.ConfigureSettingsRequest
import com.provisions.calculator.api.request.CreateSettlementRequest
import com.provisions.calculator.model.*
import com.provisions.calculator.repository.*
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class SettlementService(
    private val settlementRepository: SettlementRepository,
    private val commissionSettingsRepository: CommissionSettingsRepository,
    private val treeNodeRepository: TreeNodeRepository,
    private val treeService: TreeService
) {

    @Transactional
    fun create(tenantId: String, request: CreateSettlementRequest): Settlement {
        val settlement = Settlement(tenantId = tenantId, name = request.name)
        return settlementRepository.save(settlement)
    }

    fun findById(tenantId: String, settlementId: Long): Settlement {
        return settlementRepository.findByTenantIdAndId(tenantId, settlementId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Settlement $settlementId not found")
    }

    fun findAll(tenantId: String, status: SettlementStatus?): List<Settlement> {
        return if (status != null) {
            settlementRepository.findByTenantIdAndStatus(tenantId, status)
        } else {
            settlementRepository.findByTenantId(tenantId)
        }
    }

    @Transactional
    fun configure(tenantId: String, settlementId: Long, request: ConfigureSettingsRequest): Settlement {
        val settlement = findById(tenantId, settlementId)
        guardNotApproved(settlement)

        // Validate tree
        treeService.validate(request.tree)

        // Replace tree atomically
        treeNodeRepository.deleteByTenantIdAndSettlementId(tenantId, settlementId)
        treeNodeRepository.flush()

        // Build and save tree nodes (topological order: parents first)
        val nodeMap = mutableMapOf<String, TreeNode>()
        val pending = request.tree.toMutableList()
        while (pending.isNotEmpty()) {
            val batch = pending.filter { it.parentCustomerId == null || it.parentCustomerId in nodeMap }
            if (batch.isEmpty()) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to resolve tree node order")
            }
            for (req in batch) {
                val parentNode = req.parentCustomerId?.let { nodeMap[it] }
                val node = TreeNode(
                    tenantId = tenantId,
                    settlement = settlement,
                    customerId = req.customerId,
                    parentNode = parentNode
                )
                nodeMap[req.customerId] = treeNodeRepository.save(node)
            }
            pending.removeAll(batch)
        }

        // Replace commission settings + rates
        val existingSettings = commissionSettingsRepository.findByTenantIdAndSettlementId(tenantId, settlementId)
        if (existingSettings != null) {
            commissionSettingsRepository.delete(existingSettings)
            commissionSettingsRepository.flush()
        }

        val settings = CommissionSettings(tenantId = tenantId, settlement = settlement)
        val savedSettings = commissionSettingsRepository.save(settings)

        for (rateReq in request.rates) {
            savedSettings.rates.add(
                CommissionRate(
                    tenantId = tenantId,
                    settings = savedSettings,
                    depth = rateReq.depth,
                    ratePercent = rateReq.ratePercent
                )
            )
        }
        commissionSettingsRepository.save(savedSettings)

        // Reset status to OPEN if was CALCULATED
        if (settlement.status == SettlementStatus.CALCULATED) {
            settlement.status = SettlementStatus.OPEN
            settlementRepository.save(settlement)
        }

        return settlement
    }

    @Transactional(readOnly = true)
    fun getConfig(tenantId: String, settlementId: Long): ConfigData {
        val settlement = findById(tenantId, settlementId)
        val settings = commissionSettingsRepository.findByTenantIdAndSettlementId(tenantId, settlementId)
        // Uses JOIN FETCH to avoid N+1 on parentNode
        val nodes = treeNodeRepository.findWithParentByTenantIdAndSettlementId(tenantId, settlementId)
        // Force initialization of lazy collections within transaction
        val rates = settings?.rates?.toList() ?: emptyList()

        return ConfigData(
            settlement = settlement,
            rates = rates,
            nodes = nodes,
            parentCustomerIdMap = nodes.associate { node ->
                node.customerId to node.parentNode?.customerId
            }
        )
    }

    @Transactional
    fun approve(tenantId: String, settlementId: Long): Settlement {
        val settlement = findById(tenantId, settlementId)
        if (settlement.status != SettlementStatus.CALCULATED) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Settlement must be CALCULATED to approve, current: ${settlement.status}")
        }
        settlement.status = SettlementStatus.APPROVED
        return settlementRepository.save(settlement)
    }

    @Transactional
    fun reject(tenantId: String, settlementId: Long): Settlement {
        val settlement = findById(tenantId, settlementId)
        if (settlement.status != SettlementStatus.CALCULATED) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Settlement must be CALCULATED to reject, current: ${settlement.status}")
        }
        settlement.status = SettlementStatus.OPEN
        return settlementRepository.save(settlement)
    }

    fun guardNotApproved(settlement: Settlement) {
        if (settlement.status == SettlementStatus.APPROVED) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Settlement is APPROVED and cannot be modified")
        }
    }

    data class ConfigData(
        val settlement: Settlement,
        val rates: List<CommissionRate>,
        val nodes: List<TreeNode>,
        val parentCustomerIdMap: Map<String, String?> = emptyMap()
    )
}
