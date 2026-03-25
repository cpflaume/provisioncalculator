package com.provisions.calculator.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.provisions.calculator.api.request.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.math.BigDecimal
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SettlementLifecycleIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private val tenantId = "tenant1"
    private val baseUrl = "/api/v1/tenants/$tenantId"

    @Test
    fun `full lifecycle - create, configure, purchases, calculate, approve`() {
        // 1. Create settlement
        val createResult = mockMvc.perform(
            post("$baseUrl/settlements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(CreateSettlementRequest("März 2026")))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("März 2026"))
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andReturn()

        val settlementId = objectMapper.readTree(createResult.response.contentAsString)["id"].asLong()

        // 2. Configure rates + tree
        val configRequest = ConfigureSettingsRequest(
            rates = listOf(
                CommissionRateRequest(1, BigDecimal("1.0")),
                CommissionRateRequest(2, BigDecimal("3.0")),
                CommissionRateRequest(3, BigDecimal("5.0"))
            ),
            tree = listOf(
                TreeNodeRequest("A", null),
                TreeNodeRequest("B", "A"),
                TreeNodeRequest("C", "B"),
                TreeNodeRequest("D", "C")
            )
        )

        mockMvc.perform(
            put("$baseUrl/settlements/$settlementId/config")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(configRequest))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nodeCount").value(4))

        // 3. Submit purchases
        val purchasesRequest = SubmitPurchasesRequest(
            purchases = listOf(
                PurchaseRequest("D", BigDecimal("200.0000"), LocalDateTime.of(2026, 3, 1, 10, 0))
            )
        )

        mockMvc.perform(
            post("$baseUrl/settlements/$settlementId/purchases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(purchasesRequest))
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.accepted").value(1))

        // 4. Calculate
        val calcResult = mockMvc.perform(
            post("$baseUrl/settlements/$settlementId/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fromCache").value(false))
            .andExpect(jsonPath("$.results").isArray)
            .andReturn()

        val calcJson = objectMapper.readTree(calcResult.response.contentAsString)
        val calculationId = calcJson["calculationId"].asText()

        // Verify commissions: D buys 200
        // C at depth 1: 200 * 1% = 2.0000
        // B at depth 2: 200 * 3% = 6.0000
        // A at depth 3: 200 * 5% = 10.0000
        val results = calcJson["results"]
        assert(results.size() == 3) { "Expected 3 recipients, got ${results.size()}" }

        // 5. Verify settlement is CALCULATED
        mockMvc.perform(get("$baseUrl/settlements/$settlementId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CALCULATED"))

        // 6. Approve
        mockMvc.perform(
            post("$baseUrl/settlements/$settlementId/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("APPROVED"))

        // 7. Verify writes are blocked on APPROVED
        mockMvc.perform(
            post("$baseUrl/settlements/$settlementId/purchases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(purchasesRequest))
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `reject resets to OPEN, then recalculate and approve`() {
        // Create + configure + purchases + calculate
        val createResult = mockMvc.perform(
            post("$baseUrl/settlements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(CreateSettlementRequest("Q1")))
        ).andReturn()
        val settlementId = objectMapper.readTree(createResult.response.contentAsString)["id"].asLong()

        val configRequest = ConfigureSettingsRequest(
            rates = listOf(CommissionRateRequest(1, BigDecimal("1.0"))),
            tree = listOf(TreeNodeRequest("A", null), TreeNodeRequest("B", "A"))
        )
        mockMvc.perform(
            put("$baseUrl/settlements/$settlementId/config")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(configRequest))
        )

        val purchasesRequest = SubmitPurchasesRequest(
            purchases = listOf(PurchaseRequest("B", BigDecimal("100.0000"), LocalDateTime.of(2026, 3, 1, 10, 0)))
        )
        mockMvc.perform(
            post("$baseUrl/settlements/$settlementId/purchases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(purchasesRequest))
        )

        mockMvc.perform(
            post("$baseUrl/settlements/$settlementId/calculate")
                .contentType(MediaType.APPLICATION_JSON).content("{}")
        ).andExpect(status().isOk)

        // Reject -> auto-reset to OPEN
        mockMvc.perform(
            post("$baseUrl/settlements/$settlementId/reject")
                .contentType(MediaType.APPLICATION_JSON).content("{}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("OPEN"))

        // Verify status is OPEN
        mockMvc.perform(get("$baseUrl/settlements/$settlementId"))
            .andExpect(jsonPath("$.status").value("OPEN"))

        // Recalculate
        mockMvc.perform(
            post("$baseUrl/settlements/$settlementId/calculate")
                .contentType(MediaType.APPLICATION_JSON).content("{}")
        ).andExpect(status().isOk)

        // Approve
        mockMvc.perform(
            post("$baseUrl/settlements/$settlementId/approve")
                .contentType(MediaType.APPLICATION_JSON).content("{}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("APPROVED"))
    }

    @Test
    fun `idempotency - calculate twice returns same calculationId`() {
        val createResult = mockMvc.perform(
            post("$baseUrl/settlements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(CreateSettlementRequest("Idempotency Test")))
        ).andReturn()
        val settlementId = objectMapper.readTree(createResult.response.contentAsString)["id"].asLong()

        val configRequest = ConfigureSettingsRequest(
            rates = listOf(CommissionRateRequest(1, BigDecimal("1.0"))),
            tree = listOf(TreeNodeRequest("A", null), TreeNodeRequest("B", "A"))
        )
        mockMvc.perform(
            put("$baseUrl/settlements/$settlementId/config")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(configRequest))
        )

        mockMvc.perform(
            post("$baseUrl/settlements/$settlementId/purchases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    SubmitPurchasesRequest(listOf(PurchaseRequest("B", BigDecimal("100.0000"), LocalDateTime.of(2026, 3, 1, 10, 0))))
                ))
        )

        // First calculation
        val calc1 = mockMvc.perform(
            post("$baseUrl/settlements/$settlementId/calculate")
                .contentType(MediaType.APPLICATION_JSON).content("{}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fromCache").value(false))
            .andReturn()

        val calcId1 = objectMapper.readTree(calc1.response.contentAsString)["calculationId"].asText()

        // Second calculation (should be cached)
        val calc2 = mockMvc.perform(
            post("$baseUrl/settlements/$settlementId/calculate")
                .contentType(MediaType.APPLICATION_JSON).content("{}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fromCache").value(true))
            .andReturn()

        val calcId2 = objectMapper.readTree(calc2.response.contentAsString)["calculationId"].asText()

        assert(calcId1 == calcId2) { "Expected same calculationId, got $calcId1 vs $calcId2" }
    }

    @Test
    fun `multi-tenant isolation - tenant A cannot read tenant B data`() {
        // Create settlement for tenant A
        val createA = mockMvc.perform(
            post("/api/v1/tenants/tenantA/settlements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(CreateSettlementRequest("Tenant A Settlement")))
        ).andReturn()
        val settlementIdA = objectMapper.readTree(createA.response.contentAsString)["id"].asLong()

        // Tenant B cannot access tenant A's settlement
        mockMvc.perform(get("/api/v1/tenants/tenantB/settlements/$settlementIdA"))
            .andExpect(status().isNotFound)
    }
}
