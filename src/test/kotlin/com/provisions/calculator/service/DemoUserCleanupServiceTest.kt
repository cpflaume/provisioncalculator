package com.provisions.calculator.service

import com.provisions.calculator.model.User
import com.provisions.calculator.model.UserRole
import com.provisions.calculator.model.UserStatus
import com.provisions.calculator.repository.UserRepository
import io.mockk.*
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import jakarta.persistence.TypedQuery
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class DemoUserCleanupServiceTest {

    private val userRepository = mockk<UserRepository>()
    private val entityManager = mockk<EntityManager>()
    private val service = DemoUserCleanupService(userRepository, entityManager)

    private val nativeQuery = mockk<Query>()
    private val jpqlQuery = mockk<TypedQuery<String>>()

    @BeforeEach
    fun setUp() {
        every { nativeQuery.setParameter(any<String>(), any()) } returns nativeQuery
        every { nativeQuery.executeUpdate() } returns 0
        every { jpqlQuery.setParameter(any<String>(), any()) } returns jpqlQuery
        every { entityManager.createNativeQuery(any()) } returns nativeQuery
        every { entityManager.createQuery(any<String>(), String::class.java) } returns jpqlQuery
    }

    private fun demoUser(id: Long, expiresAt: Instant = Instant.now().minus(1, ChronoUnit.HOURS)) = User(
        id = id,
        email = "demo-uuid-$id@demo.internal",
        passwordHash = null,
        displayName = "Demo",
        role = UserRole.USER,
        status = UserStatus.ACTIVE,
        authProvider = DEMO_AUTH_PROVIDER,
        expiresAt = expiresAt
    )

    @Test
    fun `purge - skips work when no expired accounts exist`() {
        every { userRepository.findAllByAuthProviderAndExpiresAtBefore(DEMO_AUTH_PROVIDER, any()) } returns emptyList()

        service.purgeExpiredDemoAccounts()

        verify(exactly = 0) { entityManager.createNativeQuery(any()) }
        verify(exactly = 0) { userRepository.delete(any()) }
    }

    @Test
    fun `purge - deletes user and tenant data for each expired account`() {
        val user = demoUser(1L)
        val tenantId = "demo-uuid-1"
        every { userRepository.findAllByAuthProviderAndExpiresAtBefore(DEMO_AUTH_PROVIDER, any()) } returns listOf(user)
        every { jpqlQuery.resultList } returns listOf(tenantId)
        every { userRepository.delete(user) } just Runs

        service.purgeExpiredDemoAccounts()

        verify { userRepository.delete(user) }
        verify { entityManager.createNativeQuery("DELETE FROM tenants WHERE id = :tid") }
        verify { entityManager.createNativeQuery("DELETE FROM user_tenants WHERE user_id = :uid") }
        val expectedTables = listOf(
            "commission_result", "calculation", "purchase", "tree_node",
            "commission_rate", "commission_settings", "settlement"
        )
        for (table in expectedTables) {
            verify { entityManager.createNativeQuery("DELETE FROM $table WHERE tenant_id = :tid") }
        }
    }

    @Test
    fun `purge - processes multiple expired accounts independently`() {
        val user1 = demoUser(1L)
        val user2 = demoUser(2L)
        every { userRepository.findAllByAuthProviderAndExpiresAtBefore(DEMO_AUTH_PROVIDER, any()) } returns listOf(user1, user2)
        every { jpqlQuery.resultList } returnsMany listOf(listOf("demo-uuid-1"), listOf("demo-uuid-2"))
        every { userRepository.delete(any()) } just Runs

        service.purgeExpiredDemoAccounts()

        verify(exactly = 2) { userRepository.delete(any()) }
        verify(exactly = 2) { entityManager.createNativeQuery("DELETE FROM tenants WHERE id = :tid") }
        verify(exactly = 2) { entityManager.createNativeQuery("DELETE FROM user_tenants WHERE user_id = :uid") }
    }

    @Test
    fun `purge - passes correct parameters to native queries`() {
        val user = demoUser(1L)
        val tenantId = "demo-specific-tenant"
        every { userRepository.findAllByAuthProviderAndExpiresAtBefore(DEMO_AUTH_PROVIDER, any()) } returns listOf(user)
        every { jpqlQuery.resultList } returns listOf(tenantId)
        every { userRepository.delete(user) } just Runs

        service.purgeExpiredDemoAccounts()

        verify { nativeQuery.setParameter("tid", tenantId) }
        verify { nativeQuery.setParameter("uid", user.id) }
    }

    @Test
    fun `purge - queries only DEMO provider accounts`() {
        every { userRepository.findAllByAuthProviderAndExpiresAtBefore(DEMO_AUTH_PROVIDER, any()) } returns emptyList()

        service.purgeExpiredDemoAccounts()

        verify { userRepository.findAllByAuthProviderAndExpiresAtBefore(eq(DEMO_AUTH_PROVIDER), any()) }
    }
}
