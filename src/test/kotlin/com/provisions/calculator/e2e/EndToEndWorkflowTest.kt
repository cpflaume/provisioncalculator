package com.provisions.calculator.e2e

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.math.BigDecimal

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WithMockUser(roles = ["ADMIN"])
class EndToEndWorkflowTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private fun loadTestData(path: String): String =
        javaClass.classLoader.getResource("testdata/$path")!!.readText()

    // -----------------------------------------------------------------------
    // Small scenario: 5-node tree, 4 purchases, full lifecycle
    // -----------------------------------------------------------------------

    @Test
    fun `small scenario - full E2E lifecycle with 5-node tree`() {
        val tenantId = "tenant-small"
        val base = "/api/v1/tenants/$tenantId"

        // --- Step 1: Create settlement ---
        val createBody = loadTestData("small-scenario/01-create-settlement.json")
        val createResult = mockMvc.perform(
            post("$base/settlements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("März 2026"))
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.tenantId").value(tenantId))
            .andReturn()

        val settlementId = objectMapper.readTree(createResult.response.contentAsString)["id"].asLong()

        // --- Step 2: Configure settings (rates + tree) ---
        val configBody = loadTestData("small-scenario/02-configure-settings.json")
        mockMvc.perform(
            put("$base/settlements/$settlementId/config")
                .contentType(MediaType.APPLICATION_JSON)
                .content(configBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nodeCount").value(5))
            .andExpect(jsonPath("$.rates.length()").value(3))

        // --- Step 3: Verify config readback ---
        mockMvc.perform(get("$base/settlements/$settlementId/config"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nodeCount").value(5))
            .andExpect(jsonPath("$.tree.length()").value(5))
            .andExpect(jsonPath("$.rates.length()").value(3))

        // --- Step 4: Submit purchases ---
        val purchasesBody = loadTestData("small-scenario/03-submit-purchases.json")
        mockMvc.perform(
            post("$base/settlements/$settlementId/purchases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(purchasesBody)
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.accepted").value(4))

        // --- Step 5: Verify settlement is still OPEN ---
        mockMvc.perform(get("$base/settlements/$settlementId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("OPEN"))

        // --- Step 6: Calculate commissions ---
        val expected = objectMapper.readTree(loadTestData("small-scenario/04-expected-results.json"))
        val expectedTotals = expected["expectedTotals"]

        val calcResult = mockMvc.perform(
            post("$base/settlements/$settlementId/calculate")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fromCache").value(false))
            .andExpect(jsonPath("$.results.length()").value(expected["expectedRecipientCount"].asInt()))
            .andReturn()

        val calcJson = objectMapper.readTree(calcResult.response.contentAsString)
        val calculationId = calcJson["calculationId"].asText()

        // Verify commission totals per recipient
        for (result in calcJson["results"]) {
            val customerId = result["customerId"].asText()
            val actual = BigDecimal(result["totalCommission"].asText())
            val exp = BigDecimal(expectedTotals[customerId].asText())
            assert(actual.compareTo(exp) == 0) {
                "Commission for $customerId: expected $exp, got $actual"
            }
        }

        // --- Step 7: Verify settlement is CALCULATED ---
        mockMvc.perform(get("$base/settlements/$settlementId"))
            .andExpect(jsonPath("$.status").value("CALCULATED"))

        // --- Step 8: Idempotency - second calculation returns cached result ---
        mockMvc.perform(
            post("$base/settlements/$settlementId/calculate")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fromCache").value(true))
            .andExpect(jsonPath("$.calculationId").value(calculationId))

        // --- Step 9: Get results via GET ---
        mockMvc.perform(get("$base/settlements/$settlementId/calculation"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.results.length()").value(expected["expectedRecipientCount"].asInt()))

        // --- Step 10: Get per-recipient detail ---
        mockMvc.perform(get("$base/settlements/$settlementId/calculation/recipients/A"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.customerId").value("A"))
            .andExpect(jsonPath("$.details").isArray)

        // --- Step 11: Get audit trail ---
        mockMvc.perform(get("$base/settlements/$settlementId/calculation/audit"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(expected["expectedAuditEntryCount"].asInt()))

        // --- Step 12: Approve settlement ---
        mockMvc.perform(
            post("$base/settlements/$settlementId/approve")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("APPROVED"))

        // --- Step 13: Verify writes are blocked on APPROVED ---
        mockMvc.perform(
            post("$base/settlements/$settlementId/purchases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(purchasesBody)
        )
            .andExpect(status().isConflict)

        mockMvc.perform(
            put("$base/settlements/$settlementId/config")
                .contentType(MediaType.APPLICATION_JSON)
                .content(configBody)
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `small scenario - reject and recalculate flow`() {
        val tenantId = "tenant-reject"
        val base = "/api/v1/tenants/$tenantId"

        // Setup: create, configure, purchase, calculate
        val settlementId = createAndConfigure(base,
            loadTestData("small-scenario/01-create-settlement.json"),
            loadTestData("small-scenario/02-configure-settings.json"),
            loadTestData("small-scenario/03-submit-purchases.json")
        )

        mockMvc.perform(
            post("$base/settlements/$settlementId/calculate")
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isOk)

        // Reject -> status goes to OPEN
        mockMvc.perform(
            post("$base/settlements/$settlementId/reject")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("OPEN"))

        // Recalculate (cached result, same inputs)
        mockMvc.perform(
            post("$base/settlements/$settlementId/calculate")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fromCache").value(true))

        // Verify back to CALCULATED
        mockMvc.perform(get("$base/settlements/$settlementId"))
            .andExpect(jsonPath("$.status").value("CALCULATED"))

        // Now approve
        mockMvc.perform(
            post("$base/settlements/$settlementId/approve")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("APPROVED"))
    }

    // -----------------------------------------------------------------------
    // Large scenario: 5000-node tree, 10000 purchases
    // -----------------------------------------------------------------------

    @Test
    fun `large scenario - full E2E with 5000-node tree and 10000 purchases`() {
        val tenantId = "tenant-large"
        val base = "/api/v1/tenants/$tenantId"
        val nodeCount = 5000
        val purchaseCount = 10000

        // --- Step 1: Create settlement ---
        val createBody = loadTestData("large-scenario/01-create-settlement.json")
        val createResult = mockMvc.perform(
            post("$base/settlements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody)
        )
            .andExpect(status().isCreated)
            .andReturn()

        val settlementId = objectMapper.readTree(createResult.response.contentAsString)["id"].asLong()

        // --- Step 2: Generate and configure large tree + rates ---
        val ratesJson = loadTestData("large-scenario/02-rates.json")
        val treeNodes = TestDataGenerator.generateTree(objectMapper, nodeCount)
        val configRequest = TestDataGenerator.buildConfigRequest(objectMapper, ratesJson, treeNodes)

        assert(treeNodes.size() == nodeCount) { "Expected $nodeCount tree nodes, got ${treeNodes.size()}" }

        mockMvc.perform(
            put("$base/settlements/$settlementId/config")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(configRequest))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nodeCount").value(nodeCount))

        // --- Step 3: Generate and submit purchases in batches ---
        val purchasesRequest = TestDataGenerator.generatePurchases(objectMapper, treeNodes, purchaseCount)
        val allPurchases = purchasesRequest["purchases"]
        val batchSize = 1000
        var totalAccepted = 0

        for (i in 0 until purchaseCount step batchSize) {
            val end = minOf(i + batchSize, purchaseCount)
            val batch = objectMapper.createObjectNode()
            val batchArray = objectMapper.createArrayNode()
            for (j in i until end) {
                batchArray.add(allPurchases[j])
            }
            batch.set<com.fasterxml.jackson.databind.node.ArrayNode>("purchases", batchArray)

            val batchResult = mockMvc.perform(
                post("$base/settlements/$settlementId/purchases")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(batch))
            )
                .andExpect(status().isAccepted)
                .andReturn()

            val accepted = objectMapper.readTree(batchResult.response.contentAsString)["accepted"].asInt()
            totalAccepted += accepted
        }

        assert(totalAccepted == purchaseCount) {
            "Expected $purchaseCount purchases accepted, got $totalAccepted"
        }

        // --- Step 4: Calculate commissions ---
        val calcResult = mockMvc.perform(
            post("$base/settlements/$settlementId/calculate")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fromCache").value(false))
            .andExpect(jsonPath("$.results").isArray)
            .andReturn()

        val calcJson = objectMapper.readTree(calcResult.response.contentAsString)
        val resultCount = calcJson["results"].size()
        assert(resultCount > 0) { "Expected commission results, got 0" }

        // --- Step 5: Idempotency check ---
        val calcId1 = calcJson["calculationId"].asText()
        val cachedResult = mockMvc.perform(
            post("$base/settlements/$settlementId/calculate")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fromCache").value(true))
            .andReturn()

        val calcId2 = objectMapper.readTree(cachedResult.response.contentAsString)["calculationId"].asText()
        assert(calcId1 == calcId2) { "Idempotency failed: $calcId1 != $calcId2" }

        // --- Step 6: Verify settlement status ---
        mockMvc.perform(get("$base/settlements/$settlementId"))
            .andExpect(jsonPath("$.status").value("CALCULATED"))

        // --- Step 7: Audit trail returns results ---
        mockMvc.perform(get("$base/settlements/$settlementId/calculation/audit"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").isNumber)

        // --- Step 8: Approve ---
        mockMvc.perform(
            post("$base/settlements/$settlementId/approve")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("APPROVED"))
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private fun createAndConfigure(base: String, createBody: String, configBody: String, purchasesBody: String): Long {
        val createResult = mockMvc.perform(
            post("$base/settlements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody)
        ).andReturn()

        val settlementId = objectMapper.readTree(createResult.response.contentAsString)["id"].asLong()

        mockMvc.perform(
            put("$base/settlements/$settlementId/config")
                .contentType(MediaType.APPLICATION_JSON)
                .content(configBody)
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("$base/settlements/$settlementId/purchases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(purchasesBody)
        ).andExpect(status().isAccepted)

        return settlementId
    }
}
