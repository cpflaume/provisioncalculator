package com.provisions.calculator.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.provisions.calculator.api.request.CommissionRateRequest
import com.provisions.calculator.api.request.ConfigureSettingsRequest
import com.provisions.calculator.api.request.CreateSettlementRequest
import com.provisions.calculator.api.request.PurchaseRequest
import com.provisions.calculator.api.request.SubmitPurchasesRequest
import com.provisions.calculator.api.request.TreeNodeRequest
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
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WithMockUser(roles = ["ADMIN"])
class PurchaseControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper

    private val tenantId = "test-tenant"
    private val baseUrl = "/api/v1/tenants/$tenantId"

    private fun createSettlement(name: String = "Test Settlement"): Long {
        val result = mockMvc.perform(
            post("$baseUrl/settlements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(CreateSettlementRequest(name)))
        ).andReturn()
        return objectMapper.readTree(result.response.contentAsString)["id"].asLong()
    }

    private fun submitPurchase(
        settlementId: Long,
        buyerId: String = "customer-1",
        amount: BigDecimal = BigDecimal("100.00"),
    ): Long {
        val request = SubmitPurchasesRequest(
            purchases = listOf(
                PurchaseRequest(buyerId, amount, LocalDateTime.of(2026, 3, 1, 10, 0))
            )
        )
        val result = mockMvc.perform(
            post("$baseUrl/settlements/$settlementId/purchases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andReturn()
        return objectMapper.readTree(result.response.contentAsString)["ids"][0].asLong()
    }

    private fun configureAndCalculate(settlementId: Long) {
        // Minimal config: one rate + tree with root and customer-1
        val configRequest = ConfigureSettingsRequest(
            rates = listOf(CommissionRateRequest(1, BigDecimal("5.0"))),
            tree = listOf(
                TreeNodeRequest("root", null),
                TreeNodeRequest("customer-1", "root"),
            )
        )
        mockMvc.perform(
            put("$baseUrl/settlements/$settlementId/config")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(configRequest))
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("$baseUrl/settlements/$settlementId/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        ).andExpect(status().isOk)
    }

    // ---- submit response includes ids ----

    @Test
    fun `submit - response contains ids of created purchases`() {
        val settlementId = createSettlement()
        val request = SubmitPurchasesRequest(
            purchases = listOf(
                PurchaseRequest("customer-1", BigDecimal("100.00"), LocalDateTime.of(2026, 3, 1, 10, 0)),
                PurchaseRequest("customer-2", BigDecimal("200.00"), LocalDateTime.of(2026, 3, 2, 10, 0))
            )
        )

        mockMvc.perform(
            post("$baseUrl/settlements/$settlementId/purchases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.accepted").value(2))
            .andExpect(jsonPath("$.ids").isArray)
            .andExpect(jsonPath("$.ids.length()").value(2))
    }

    // ---- delete ----

    @Test
    fun `delete - existing purchase returns 204 and removes it from list`() {
        val settlementId = createSettlement()
        val purchaseId = submitPurchase(settlementId)

        mockMvc.perform(delete("$baseUrl/settlements/$settlementId/purchases/$purchaseId"))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("$baseUrl/settlements/$settlementId/purchases"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.content").isEmpty)
    }

    @Test
    fun `delete - only removes the targeted purchase, others remain`() {
        val settlementId = createSettlement()
        val id1 = submitPurchase(settlementId, buyerId = "customer-1")
        submitPurchase(settlementId, buyerId = "customer-2")

        mockMvc.perform(delete("$baseUrl/settlements/$settlementId/purchases/$id1"))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("$baseUrl/settlements/$settlementId/purchases"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].buyerCustomerId").value("customer-2"))
    }

    @Test
    fun `delete - nonexistent purchase returns 404`() {
        val settlementId = createSettlement()

        mockMvc.perform(delete("$baseUrl/settlements/$settlementId/purchases/999999"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `delete - purchase belonging to different settlement returns 404`() {
        val settlement1 = createSettlement("Settlement 1")
        val settlement2 = createSettlement("Settlement 2")
        val purchaseId = submitPurchase(settlement1)

        mockMvc.perform(delete("$baseUrl/settlements/$settlement2/purchases/$purchaseId"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `delete - purchase belonging to different tenant returns 404`() {
        val settlementId = createSettlement()
        val purchaseId = submitPurchase(settlementId)

        mockMvc.perform(delete("/api/v1/tenants/other-tenant/settlements/$settlementId/purchases/$purchaseId"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `delete - approved settlement returns 409`() {
        val settlementId = createSettlement()
        val purchaseId = submitPurchase(settlementId)

        // approve requires CALCULATED status: configure rates+tree, then calculate
        configureAndCalculate(settlementId)

        mockMvc.perform(
            post("$baseUrl/settlements/$settlementId/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        ).andExpect(status().isOk)

        mockMvc.perform(delete("$baseUrl/settlements/$settlementId/purchases/$purchaseId"))
            .andExpect(status().isConflict)
    }

    @Test
    fun `delete - unauthenticated returns 401`() {
        val settlementId = createSettlement()
        val purchaseId = submitPurchase(settlementId)

        mockMvc.perform(
            delete("$baseUrl/settlements/$settlementId/purchases/$purchaseId")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())
        )
            .andExpect(status().isUnauthorized)
    }
}
