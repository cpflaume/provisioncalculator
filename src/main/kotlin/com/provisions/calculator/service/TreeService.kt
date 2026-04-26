package com.provisions.calculator.service

import com.provisions.calculator.api.request.TreeNodeRequest
import com.provisions.calculator.engine.TreeNodeMemento
import com.provisions.calculator.repository.TreeNodeRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class TreeService(private val treeNodeRepository: TreeNodeRepository) {

    @Transactional(readOnly = true)
    fun loadTreeIntoMemory(tenantId: String, settlementId: Long): Map<String, TreeNodeMemento> {
        // Uses JOIN FETCH to eagerly load parentNode in a single query, avoiding N+1
        val nodes = treeNodeRepository.findWithParentByTenantIdAndSettlementId(tenantId, settlementId)

        val childrenMap = mutableMapOf<String, MutableList<String>>()

        for (node in nodes) {
            val parentCustomerId = node.parentNode?.customerId
            if (parentCustomerId != null) {
                childrenMap.getOrPut(parentCustomerId) { mutableListOf() }.add(node.customerId)
            }
        }

        return nodes.associate { node ->
            val parentCustomerId = node.parentNode?.customerId
            node.customerId to TreeNodeMemento(
                customerId = node.customerId,
                parentCustomerId = parentCustomerId,
                children = childrenMap[node.customerId] ?: emptyList()
            )
        }
    }

    fun validate(nodes: List<TreeNodeRequest>) {
        // Empty tree is allowed: rates and tree may be saved independently.
        if (nodes.isEmpty()) return

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

        // Build children map for DFS traversal
        val childrenMap = mutableMapOf<String, MutableList<String>>()
        for (node in nodes) {
            if (node.parentCustomerId != null) {
                childrenMap.getOrPut(node.parentCustomerId) { mutableListOf() }.add(node.customerId)
            }
        }

        // Single DFS from root: detects cycles and verifies all nodes are reachable
        val visited = mutableSetOf<String>()
        val stack = mutableSetOf<String>()
        val root = roots[0].customerId

        fun dfs(nodeId: String) {
            if (nodeId in stack) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Tree contains a cycle")
            }
            if (nodeId in visited) return
            visited.add(nodeId)
            stack.add(nodeId)
            for (child in childrenMap[nodeId] ?: emptyList()) {
                dfs(child)
            }
            stack.remove(nodeId)
        }

        dfs(root)

        if (visited.size != nodes.size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Tree contains nodes not reachable from root")
        }
    }
}
