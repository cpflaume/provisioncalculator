package com.provisions.calculator.service

import com.provisions.calculator.engine.CalculationContext
import com.provisions.calculator.engine.CommissionLineItem
import com.provisions.calculator.engine.CommissionRuleEngine
import com.provisions.calculator.engine.TreeNodeMemento
import com.provisions.calculator.model.*
import com.provisions.calculator.repository.*
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

class CalculationServiceTest {

    private val settlementService = mockk<SettlementService>()
    private val treeService = mockk<TreeService>()
    private val purchaseRepository = mockk<PurchaseRepository>()
    private val commissionSettingsRepository = mockk<CommissionSettingsRepository>()
    private val calculationRepository = mockk<CalculationRepository>()
    private val commissionResultRepository = mockk<CommissionResultRepository>()
    private val settlementRepository = mockk<SettlementRepository>()
    private val ruleEngine = mockk<CommissionRuleEngine>()

    private lateinit var service: CalculationService

    private val settlement = Settlement(id = 1L, tenantId = "t1", name = "Test", status = SettlementStatus.OPEN)
    private val settings = CommissionSettings(id = 1L, tenantId = "t1", settlement = settlement)

    @BeforeEach
    fun setup() {
        service = CalculationService(
            settlementService, treeService, purchaseRepository, commissionSettingsRepository,
            calculationRepository, commissionResultRepository, settlementRepository, ruleEngine
        )
        settings.rates.add(CommissionRate(1L, "t1", settings, 1, BigDecimal("1.0")))
    }

    @Test
    fun `calculate returns cached result when input hash matches`() {
        val purchase = Purchase(1L, "t1", settlement, "B", BigDecimal("100.0000"), LocalDateTime.now())
        val existingCalc = Calculation(UUID.randomUUID(), "t1", settlement, "somehash")
        val existingResult = CommissionResult(1L, "t1", settlement, existingCalc, "A", purchase, BigDecimal("1.0000"), 1, "DEPTH_BASED")

        every { settlementService.findById("t1", 1L) } returns settlement
        every { settlementService.guardNotApproved(settlement) } just Runs
        every { commissionSettingsRepository.findByTenantIdAndSettlementId("t1", 1L) } returns settings
        every { purchaseRepository.findByTenantIdAndSettlementId("t1", 1L) } returns listOf(purchase)
        every { calculationRepository.findByTenantIdAndSettlementIdAndInputHash("t1", 1L, any()) } returns existingCalc
        every { commissionResultRepository.findByCalculationId(existingCalc.id) } returns listOf(existingResult)

        val result = service.calculate("t1", 1L)

        assertTrue(result.fromCache)
        assertEquals(existingCalc.id, result.calculation.id)
        verify(exactly = 0) { ruleEngine.execute(any()) }
    }

    @Test
    fun `calculate runs engine when no cached result`() {
        val purchase = Purchase(1L, "t1", settlement, "B", BigDecimal("100.0000"), LocalDateTime.now())
        val treeMap = mapOf(
            "A" to TreeNodeMemento("A", null, listOf("B")),
            "B" to TreeNodeMemento("B", "A", emptyList())
        )
        val lineItem = CommissionLineItem("A", 1L, BigDecimal("1.0000"), 1, "DEPTH_BASED")

        every { settlementService.findById("t1", 1L) } returns settlement
        every { settlementService.guardNotApproved(settlement) } just Runs
        every { commissionSettingsRepository.findByTenantIdAndSettlementId("t1", 1L) } returns settings
        every { purchaseRepository.findByTenantIdAndSettlementId("t1", 1L) } returns listOf(purchase)
        every { calculationRepository.findByTenantIdAndSettlementIdAndInputHash("t1", 1L, any()) } returns null
        every { treeService.loadTreeIntoMemory("t1", 1L) } returns treeMap
        every { ruleEngine.execute(any<CalculationContext>()) } returns listOf(lineItem)
        every { calculationRepository.save(any<Calculation>()) } answers { firstArg() }
        every { commissionResultRepository.saveAll(any<List<CommissionResult>>()) } answers { firstArg<List<CommissionResult>>() }
        every { settlementRepository.save(any<Settlement>()) } answers { firstArg() }

        val result = service.calculate("t1", 1L)

        assertFalse(result.fromCache)
        assertEquals(SettlementStatus.CALCULATED, settlement.status)
        verify(exactly = 1) { ruleEngine.execute(any()) }
        verify(exactly = 1) { calculationRepository.save(any()) }
    }
}
