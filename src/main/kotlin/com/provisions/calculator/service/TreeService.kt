package com.provisions.calculator.service

import com.provisions.calculator.api.request.TreeNodeRequest
import com.provisions.calculator.engine.TreeNodeMemento
import com.provisions.calculator.repository.TreeNodeRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class TreeService(private val treeNodeRepository: TreeNodeRepository) {

    fun loadTreeIntoMemory(tenantId: String, settlementId: Long): Map<String, TreeNodeMemento> {
        val nodes = treeNodeRepository.findByTenantIdAndSettlementId(tenantId, settlementId)

        val nodeById = nodes.associateBy { it.id }
        val childrenMap = mutableMapOf<String, MutableList<String>>()

        for (node in nodes) {
            val parentCustomerId = node.parentNode?.let { nodeById[it.id]?.customerId }
            if (parentCustomerId != null) {
                childrenMap.getOrPut(parentCustomerId) { mutableListOf() }.add(node.customerId)
            }
        }

        return nodes.associate { node ->
            val parentCustomerId = node.parentNode?.let { nodeById[it.id]?.customerId }
            node.customerId to TreeNodeMemento(
                customerId = node.customerId,
                parentCustomerId = parentCustomerId,
                children = childrenMap[node.customerId] ?: emptyList()
            )
        }
    }

    fun validate(nodes: List<TreeNodeRequest>) {
        if (nodes.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Tree must contain at least one node")
        }

        val customerIds = nodes.map { it.customerId }.toSet()
        if (customerIds.size != nodes.size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate customer IDs in tree")
        }

        val roots = nodes.filter { it.parentCustomerId == null }
        if (roots.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Tree must have at least one root node")
        }
        if (roots.size > 1) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Tree must have exactly one root node, found ${roots.size}")
        }

        for (node in nodes) {
            if (node.parentCustomerId != null && node.parentCustomerId !in customerIds) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Parent '${node.parentCustomerId}' not found for node '${node.customerId}'"
                )
            }
        }

        // Cycle detection via DFS
        val childrenMap = mutableMapOf<String, MutableList<String>>()
        for (node in nodes) {
            if (node.parentCustomerId != null) {
                childrenMap.getOrPut(node.parentCustomerId) { mutableListOf() }.add(node.customerId)
            }
        }

        val visited = mutableSetOf<String>()
        val stack = mutableSetOf<String>()

        fun hasCycle(nodeId: String): Boolean {
            if (nodeId in stack) return true
            if (nodeId in visited) return false
            visited.add(nodeId)
            stack.add(nodeId)
            for (child in childrenMap[nodeId] ?: emptyList()) {
                if (hasCycle(child)) return true
            }
            stack.remove(nodeId)
            return false
        }

        for (node in nodes) {
            if (hasCycle(node.customerId)) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Tree contains a cycle")
            }
        }
    }
}
