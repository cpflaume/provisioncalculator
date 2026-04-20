package com.provisions.calculator.e2e

import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime

/**
 * Generates a balanced tree of N nodes and purchases for leaf nodes.
 *
 * Tree structure: each non-leaf node has [branchingFactor] children.
 * Nodes are named "C-0001", "C-0002", etc. C-0001 is the root.
 */
object TestDataGenerator {

    fun generateTree(objectMapper: JsonMapper, nodeCount: Int, branchingFactor: Int = 5): ArrayNode {
        val tree = objectMapper.createArrayNode()

        // Root node
        val root = objectMapper.createObjectNode()
        root.put("customerId", formatNodeId(1))
        root.putNull("parentCustomerId")
        tree.add(root)

        // BFS-style: assign children to each node
        var nextId = 2
        val queue = ArrayDeque<Int>()
        queue.add(1)

        while (nextId <= nodeCount && queue.isNotEmpty()) {
            val parentId = queue.removeFirst()
            val childrenToCreate = minOf(branchingFactor, nodeCount - nextId + 1)
            for (i in 0 until childrenToCreate) {
                val node = objectMapper.createObjectNode()
                node.put("customerId", formatNodeId(nextId))
                node.put("parentCustomerId", formatNodeId(parentId))
                tree.add(node)
                queue.add(nextId)
                nextId++
                if (nextId > nodeCount) break
            }
        }

        return tree
    }

    fun generatePurchases(
        objectMapper: JsonMapper,
        treeNodes: ArrayNode,
        purchaseCount: Int
    ): ObjectNode {
        // Collect leaf nodes (nodes that are never a parent)
        val allIds = mutableSetOf<String>()
        val parentIds = mutableSetOf<String>()
        for (node in treeNodes) {
            allIds.add(node["customerId"].asText())
            val parent = node["parentCustomerId"]
            if (!parent.isNull) {
                parentIds.add(parent.asText())
            }
        }
        val leafNodes = (allIds - parentIds).toList().sorted()

        val purchases = objectMapper.createArrayNode()
        val baseTime = LocalDateTime.of(2026, 3, 1, 10, 0)

        for (i in 0 until purchaseCount) {
            val buyerId = leafNodes[i % leafNodes.size]
            val amount = BigDecimal(50 + (i % 200)).setScale(4, RoundingMode.HALF_UP)
            val purchase = objectMapper.createObjectNode()
            purchase.put("buyerCustomerId", buyerId)
            purchase.put("amount", amount)
            purchase.put("purchasedAt", baseTime.plusHours(i.toLong()).toString())
            purchases.add(purchase)
        }

        val wrapper = objectMapper.createObjectNode()
        wrapper.set("purchases", purchases)
        return wrapper
    }

    fun buildConfigRequest(
        objectMapper: JsonMapper,
        ratesJson: String,
        treeNodes: ArrayNode
    ): ObjectNode {
        val ratesNode = objectMapper.readTree(ratesJson) as ObjectNode
        ratesNode.set("tree", treeNodes)
        return ratesNode
    }

    private fun formatNodeId(id: Int): String = "C-%04d".format(id)
}
