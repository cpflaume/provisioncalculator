package com.provisions.calculator.performance

import tools.jackson.databind.json.JsonMapper
import com.provisions.calculator.api.request.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.springframework.beans.factory.annotation.Autowired
import com.provisions.calculator.MockMvcTestConfig
import org.springframework.context.annotation.Import
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import kotlin.random.Random

@SpringBootTest
@Import(MockMvcTestConfig::class)
@ActiveProfiles("test")
@Tag("performance")
@TestMethodOrder(OrderAnnotation::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = [
    "spring.jpa.properties.hibernate.jdbc.batch_size=1000",
    "spring.jpa.properties.hibernate.order_inserts=true",
    "logging.level.org.hibernate.SQL=WARN"
])
class PerformanceTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: JsonMapper

    private val tenantId = "perf-tenant"
    private val baseUrl = "/api/v1/tenants/$tenantId"
    private val results = mutableListOf<PerfResult>()

    data class PerfResult(
        val treeSize: Int,
        val purchaseCount: Int,
        val configureMs: Long,
        val purchaseSubmitMs: Long,
        val calculateMs: Long,
        val getResultsMs: Long,
        val totalMs: Long
    )

    @Test
    @Order(1)
    fun `perf - 100 nodes`() = runScenario(100)

    @Test
    @Order(2)
    fun `perf - 1000 nodes`() = runScenario(1_000)

    @Test
    @Order(3)
    fun `perf - 10000 nodes`() = runScenario(10_000)

    @Test
    @Order(4)
    fun `perf - 100000 nodes`() = runScenario(100_000)

    private fun runScenario(treeSize: Int) {
        val tree = generateTree(treeSize)
        val purchases = generatePurchases(tree, seed = 42L + treeSize)

        val settlementId = createSettlement("Perf-$treeSize")
        val configureMs = configureSettlement(settlementId, tree)
        val purchaseMs = submitPurchasesInBatches(settlementId, purchases)
        val calculateMs = triggerCalculation(settlementId)
        val getResultsMs = getResults(settlementId)
        val totalMs = configureMs + purchaseMs + calculateMs + getResultsMs

        results.add(PerfResult(treeSize, purchases.size, configureMs, purchaseMs, calculateMs, getResultsMs, totalMs))

        println("Tree $treeSize: configure=${configureMs}ms, purchases=${purchaseMs}ms (${purchases.size}), " +
                "calculate=${calculateMs}ms, results=${getResultsMs}ms, total=${totalMs}ms")
    }

    private fun generateTree(size: Int): List<TreeNodeRequest> {
        val random = Random(42)
        val nodes = mutableListOf<TreeNodeRequest>()
        nodes.add(TreeNodeRequest(customerId = "node-0", parentCustomerId = null))
        for (i in 1 until size) {
            val parentIndex = random.nextInt(i)
            nodes.add(TreeNodeRequest(
                customerId = "node-$i",
                parentCustomerId = "node-$parentIndex"
            ))
        }
        return nodes
    }

    private fun generatePurchases(tree: List<TreeNodeRequest>, seed: Long): List<PurchaseRequest> {
        val random = Random(seed)
        val purchases = mutableListOf<PurchaseRequest>()
        val baseTime = Instant.parse("2026-03-01T10:00:00Z")
        for (node in tree) {
            val count = random.nextInt(6) // 0 to 5
            repeat(count) {
                val amount = BigDecimal(random.nextInt(99001) + 1000)
                    .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
                purchases.add(PurchaseRequest(
                    buyerCustomerId = node.customerId,
                    amount = amount,
                    purchasedAt = baseTime.plusSeconds(purchases.size.toLong() * 60)
                ))
            }
        }
        return purchases
    }

    private fun createSettlement(name: String): Long {
        val result = mockMvc.perform(
            post("$baseUrl/settlements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(CreateSettlementRequest(name)))
        )
            .andExpect(status().isCreated)
            .andReturn()
        return objectMapper.readTree(result.response.contentAsString)["id"].asLong()
    }

    private fun configureSettlement(settlementId: Long, tree: List<TreeNodeRequest>): Long {
        val request = ConfigureSettingsRequest(
            rates = listOf(
                CommissionRateRequest(1, BigDecimal("10.0")),
                CommissionRateRequest(2, BigDecimal("5.0")),
                CommissionRateRequest(3, BigDecimal("2.0"))
            ),
            tree = tree
        )
        val json = objectMapper.writeValueAsString(request)
        val start = System.nanoTime()
        mockMvc.perform(
            put("$baseUrl/settlements/$settlementId/config")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
        ).andExpect(status().isOk)
        return (System.nanoTime() - start) / 1_000_000
    }

    private fun submitPurchasesInBatches(settlementId: Long, purchases: List<PurchaseRequest>, batchSize: Int = 5000): Long {
        var totalMs = 0L
        purchases.chunked(batchSize).forEach { batch ->
            val request = SubmitPurchasesRequest(purchases = batch)
            val json = objectMapper.writeValueAsString(request)
            val start = System.nanoTime()
            mockMvc.perform(
                post("$baseUrl/settlements/$settlementId/purchases")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json)
            ).andExpect(status().isAccepted)
            totalMs += (System.nanoTime() - start) / 1_000_000
        }
        return totalMs
    }

    private fun triggerCalculation(settlementId: Long): Long {
        val start = System.nanoTime()
        mockMvc.perform(
            post("$baseUrl/settlements/$settlementId/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        ).andExpect(status().isOk)
        return (System.nanoTime() - start) / 1_000_000
    }

    private fun getResults(settlementId: Long): Long {
        val start = System.nanoTime()
        mockMvc.perform(
            get("$baseUrl/settlements/$settlementId/calculation")
        ).andExpect(status().isOk)
        return (System.nanoTime() - start) / 1_000_000
    }

    @AfterAll
    fun reportResults() {
        val sb = StringBuilder()
        sb.appendLine("## Performance Test Results")
        sb.appendLine()
        sb.appendLine("| Tree Size | Purchases | Configure (s) | Submit (s) | Calculate (s) | Results (s) | Total (s) |")
        sb.appendLine("|----------:|----------:|--------------:|-----------:|--------------:|------------:|----------:|")
        for (r in results) {
            sb.appendLine("| %,d | %,d | %.3f | %.3f | %.3f | %.3f | %.3f |".format(
                r.treeSize,
                r.purchaseCount,
                r.configureMs / 1000.0,
                r.purchaseSubmitMs / 1000.0,
                r.calculateMs / 1000.0,
                r.getResultsMs / 1000.0,
                r.totalMs / 1000.0
            ))
        }

        val outputPath = System.getProperty("PERF_RESULTS_FILE") ?: "build/performance-results.md"
        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(sb.toString())

        println()
        println(sb.toString())
    }
}
