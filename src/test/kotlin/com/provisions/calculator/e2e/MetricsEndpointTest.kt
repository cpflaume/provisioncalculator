package com.provisions.calculator.e2e

import tools.jackson.databind.json.JsonMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import com.provisions.calculator.MockMvcTestConfig
import org.springframework.context.annotation.Import
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.math.BigDecimal
import java.math.RoundingMode

@SpringBootTest
@Import(MockMvcTestConfig::class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WithMockUser(roles = ["ADMIN"])
class MetricsEndpointTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: JsonMapper

    private fun loadTestData(path: String): String =
        javaClass.classLoader.getResource("testdata/$path")!!.readText()

    // -----------------------------------------------------------------------
    // Option C: Lifecycle-Stages — metrics at each settlement phase
    // -----------------------------------------------------------------------

    @Test
    fun `settlement metrics respond correctly at each lifecycle stage`() {
        val tenantId = "tenant-metrics-lifecycle"
        val base = "/api/v1/tenants/$tenantId"

        // --- Stage 0: Create settlement ---
        val createBody = loadTestData("small-scenario/01-create-settlement.json")
        val createResult = mockMvc.perform(
            post("$base/settlements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody)
        ).andExpect(status().isCreated).andReturn()
        val settlementId = objectMapper.readTree(createResult.response.contentAsString)["id"].asLong()

        // --- Stage 1: No purchases, no calculation → empty stats ---
        mockMvc.perform(get("$base/settlements/$settlementId/metrics"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.purchases.count").value(0))
            .andExpect(jsonPath("$.purchases.totalAmount").value(0))
            .andExpect(jsonPath("$.commissions").isEmpty)
            .andExpect(jsonPath("$.crossCheck").isEmpty)

        // --- Stage 2: Configure + submit purchases, but no calculation ---
        val configBody = loadTestData("small-scenario/02-configure-settings.json")
        mockMvc.perform(
            put("$base/settlements/$settlementId/config")
                .contentType(MediaType.APPLICATION_JSON)
                .content(configBody)
        ).andExpect(status().isOk)

        val purchasesBody = loadTestData("small-scenario/03-submit-purchases.json")
        mockMvc.perform(
            post("$base/settlements/$settlementId/purchases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(purchasesBody)
        ).andExpect(status().isAccepted)

        // Purchase stats present, commission/crossCheck still null
        mockMvc.perform(get("$base/settlements/$settlementId/metrics"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.purchases.count").value(4))
            .andExpect(jsonPath("$.purchases.totalAmount").value(650.0))
            .andExpect(jsonPath("$.purchases.average").value(162.5))
            .andExpect(jsonPath("$.purchases.min").value(50.0))
            .andExpect(jsonPath("$.purchases.max").value(300.0))
            .andExpect(jsonPath("$.purchases.outliers.length()").value(0))
            .andExpect(jsonPath("$.commissions").isEmpty)
            .andExpect(jsonPath("$.crossCheck").isEmpty)

        // --- Stage 3: Calculate → full metrics available ---
        mockMvc.perform(
            post("$base/settlements/$settlementId/calculate")
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isOk)

        val metricsResult = mockMvc.perform(get("$base/settlements/$settlementId/metrics"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.purchases.count").value(4))
            .andExpect(jsonPath("$.commissions").isNotEmpty)
            .andExpect(jsonPath("$.crossCheck").isNotEmpty)
            // Commission totals
            .andExpect(jsonPath("$.commissions.totalCommission").value(44.0))
            .andExpect(jsonPath("$.commissions.recipientCount").value(3))
            // Depth distribution: 3 depth levels
            .andExpect(jsonPath("$.commissions.byDepth.length()").value(3))
            // Cross-check: actual < theoretical → negative deviation
            .andExpect(jsonPath("$.crossCheck.totalPurchaseVolume").value(650.0))
            .andExpect(jsonPath("$.crossCheck.theoreticalTotal").value(58.5))
            .andExpect(jsonPath("$.crossCheck.actualTotal").value(44.0))
            .andReturn()

        // Verify cross-check deviation is negative (plausible)
        val metrics = objectMapper.readTree(metricsResult.response.contentAsString)
        val deviation = BigDecimal(metrics["crossCheck"]["deviationPercent"].asText())
        assert(deviation < BigDecimal.ZERO) {
            "Expected negative deviation (actual < theoretical), got $deviation"
        }

        // Verify depth buckets contain exact amounts
        val byDepth = metrics["commissions"]["byDepth"]
        val depthAmounts = mutableMapOf<Int, BigDecimal>()
        for (bucket in byDepth) {
            depthAmounts[bucket["depth"].asInt()] = BigDecimal(bucket["totalAmount"].asText())
        }
        assertBigDecimalEquals(BigDecimal("32.5000"), depthAmounts[1]!!, "Depth 1 commission")
        assertBigDecimalEquals(BigDecimal("10.5000"), depthAmounts[2]!!, "Depth 2 commission")
        assertBigDecimalEquals(BigDecimal("1.0000"), depthAmounts[3]!!, "Depth 3 commission")

        // Verify theoretical depth lines in cross-check
        val theoreticalByDepth = metrics["crossCheck"]["theoreticalByDepth"]
        assert(theoreticalByDepth.size() == 3) { "Expected 3 theoretical depth lines" }

        // --- Stage 4: Approve → metrics still accessible ---
        mockMvc.perform(
            post("$base/settlements/$settlementId/approve")
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isOk)

        mockMvc.perform(get("$base/settlements/$settlementId/metrics"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.purchases.count").value(4))
            .andExpect(jsonPath("$.commissions.totalCommission").value(44.0))
            .andExpect(jsonPath("$.crossCheck.actualTotal").value(44.0))
    }

    // -----------------------------------------------------------------------
    // Option D: Multi-Settlement tenant overview
    // -----------------------------------------------------------------------

    @Test
    fun `tenant overview aggregates across multiple settlements correctly`() {
        val tenantId = "tenant-overview-multi"
        val base = "/api/v1/tenants/$tenantId"

        // --- Settlement 1: APPROVED (configured, purchases, calculated, approved) ---
        val s1Id = createSettlement(base, "Abrechnung Q1")
        configureSmallScenario(base, s1Id)
        submitSmallPurchases(base, s1Id)
        mockMvc.perform(
            post("$base/settlements/$s1Id/calculate").contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isOk)
        mockMvc.perform(
            post("$base/settlements/$s1Id/approve").contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isOk)

        // --- Settlement 2: CALCULATED (same config, different purchases) ---
        val s2Id = createSettlement(base, "Abrechnung Q2")
        configureSmallScenario(base, s2Id)
        // Submit different purchase amounts for settlement 2
        val s2Purchases = """{ "purchases": [
            { "buyerCustomerId": "E", "amount": 500.00, "purchasedAt": "2026-06-01T10:00:00Z" },
            { "buyerCustomerId": "D", "amount": 500.00, "purchasedAt": "2026-06-02T14:30:00Z" }
        ]}"""
        mockMvc.perform(
            post("$base/settlements/$s2Id/purchases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(s2Purchases)
        ).andExpect(status().isAccepted)
        mockMvc.perform(
            post("$base/settlements/$s2Id/calculate").contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isOk)

        // --- Settlement 3: OPEN (no config, no purchases) ---
        createSettlement(base, "Abrechnung Q3")

        // --- Verify tenant overview ---
        val overviewResult = mockMvc.perform(get("$base/metrics/overview"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.settlementsByStatus.APPROVED").value(1))
            .andExpect(jsonPath("$.settlementsByStatus.CALCULATED").value(1))
            .andExpect(jsonPath("$.settlementsByStatus.OPEN").value(1))
            .andReturn()

        val overview = objectMapper.readTree(overviewResult.response.contentAsString)

        // Total purchase volume: S1(650) + S2(1000) + S3(0) = 1650
        val totalPurchaseVolume = BigDecimal(overview["totalPurchaseVolume"].asText())
        assertBigDecimalEquals(BigDecimal("1650.0000"), totalPurchaseVolume, "Total purchase volume")

        // Approved purchase volume: only S1(650) is APPROVED
        val approvedPurchaseVolume = BigDecimal(overview["approvedPurchaseVolume"].asText())
        assertBigDecimalEquals(BigDecimal("650.0000"), approvedPurchaseVolume, "Approved purchase volume")

        // Other purchase volume: S2(1000) + S3(0) = 1000
        val otherPurchaseVolume = BigDecimal(overview["otherPurchaseVolume"].asText())
        assertBigDecimalEquals(BigDecimal("1000.0000"), otherPurchaseVolume, "Other purchase volume")

        // Total commission: S1(44.00) + S2 commission
        // S2: E buys 500, D buys 500
        //   E(500): C gets 25(d1), B gets 15(d2), A gets 5(d3)
        //   D(500): B gets 25(d1), A gets 15(d2)
        //   S2 total = 25+15+5+25+15 = 85
        // Grand total = 44 + 85 = 129
        val totalCommission = BigDecimal(overview["totalCommission"].asText())
        assertBigDecimalEquals(BigDecimal("129.0000"), totalCommission, "Total commission")

        // Commission rate = 129 / 1650 * 100 = 7.82%
        val expectedRate = BigDecimal("129").multiply(BigDecimal("100"))
            .divide(BigDecimal("1650"), 2, RoundingMode.HALF_UP)
        val actualRate = BigDecimal(overview["averageCommissionRatePercent"].asText())
        assertBigDecimalEquals(expectedRate, actualRate, "Commission rate")

        // S3 contributes nothing to totals (no purchases, no calculation)
        assert(totalPurchaseVolume > BigDecimal.ZERO) { "Should have purchase volume" }
    }

    @Test
    fun `tenant overview returns zeros for new tenant with no data`() {
        val tenantId = "tenant-empty"
        val base = "/api/v1/tenants/$tenantId"

        mockMvc.perform(get("$base/metrics/overview"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalPurchaseVolume").value(0))
            .andExpect(jsonPath("$.approvedPurchaseVolume").value(0))
            .andExpect(jsonPath("$.otherPurchaseVolume").value(0))
            .andExpect(jsonPath("$.totalCommission").value(0))
            .andExpect(jsonPath("$.averageCommissionRatePercent").value(0))
            .andExpect(jsonPath("$.settlementsByStatus").isEmpty)
    }

    @Test
    fun `tenant overview uses only latest calculation per settlement`() {
        val tenantId = "tenant-recalc"
        val base = "/api/v1/tenants/$tenantId"

        // Create settlement, configure, add purchases, calculate
        val sId = createSettlement(base, "Recalc Test")
        configureSmallScenario(base, sId)
        submitSmallPurchases(base, sId)
        mockMvc.perform(
            post("$base/settlements/$sId/calculate").contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isOk)

        // Record commission after first calculation
        val overview1 = objectMapper.readTree(
            mockMvc.perform(get("$base/metrics/overview"))
                .andExpect(status().isOk).andReturn().response.contentAsString
        )
        val commission1 = BigDecimal(overview1["totalCommission"].asText())

        // Reject, add more purchases, recalculate → new calculation row
        mockMvc.perform(
            post("$base/settlements/$sId/reject").contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isOk)

        val extraPurchases = """{ "purchases": [
            { "buyerCustomerId": "B", "amount": 1000.00, "purchasedAt": "2026-03-10T12:00:00Z" }
        ]}"""
        mockMvc.perform(
            post("$base/settlements/$sId/purchases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(extraPurchases)
        ).andExpect(status().isAccepted)

        mockMvc.perform(
            post("$base/settlements/$sId/calculate").contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isOk).andExpect(jsonPath("$.fromCache").value(false))

        // Verify overview uses only the LATEST calculation (higher commission)
        val overview2 = objectMapper.readTree(
            mockMvc.perform(get("$base/metrics/overview"))
                .andExpect(status().isOk).andReturn().response.contentAsString
        )
        val commission2 = BigDecimal(overview2["totalCommission"].asText())

        assert(commission2 > commission1) {
            "After adding purchases and recalculating, commission should increase: was $commission1, now $commission2"
        }

        // Total purchases should now include the extra 1000
        val totalPurchases = BigDecimal(overview2["totalPurchaseVolume"].asText())
        assertBigDecimalEquals(BigDecimal("1650.0000"), totalPurchases, "Total after extra purchase")
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun createSettlement(base: String, name: String): Long {
        val body = """{ "name": "$name" }"""
        val result = mockMvc.perform(
            post("$base/settlements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readTree(result.response.contentAsString)["id"].asLong()
    }

    private fun configureSmallScenario(base: String, settlementId: Long) {
        val configBody = loadTestData("small-scenario/02-configure-settings.json")
        mockMvc.perform(
            put("$base/settlements/$settlementId/config")
                .contentType(MediaType.APPLICATION_JSON)
                .content(configBody)
        ).andExpect(status().isOk)
    }

    private fun submitSmallPurchases(base: String, settlementId: Long) {
        val purchasesBody = loadTestData("small-scenario/03-submit-purchases.json")
        mockMvc.perform(
            post("$base/settlements/$settlementId/purchases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(purchasesBody)
        ).andExpect(status().isAccepted)
    }

    private fun assertBigDecimalEquals(expected: BigDecimal, actual: BigDecimal, label: String) {
        assert(expected.setScale(2, RoundingMode.HALF_UP).compareTo(actual.setScale(2, RoundingMode.HALF_UP)) == 0) {
            "$label: expected $expected, got $actual"
        }
    }
}
