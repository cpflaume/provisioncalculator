package com.provisions.calculator.service

import com.provisions.calculator.repository.UserRepository
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class DemoUserCleanupService(
    private val userRepository: UserRepository,
    private val entityManager: EntityManager
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val tenantScopedTables = listOf(
        "commission_result",
        "calculation",
        "purchase",
        "tree_node",
        "commission_rate",
        "commission_settings",
        "settlement"
    )

    @Scheduled(fixedDelayString = "\${app.demo.cleanup-interval-ms:3600000}", initialDelay = 60_000)
    @Transactional
    fun purgeExpiredDemoAccounts() {
        val expired = userRepository.findAllByAuthProviderAndExpiresAtBefore(DEMO_AUTH_PROVIDER, Instant.now())
        if (expired.isEmpty()) return

        log.info("Purging {} expired demo accounts", expired.size)
        for (user in expired) {
            val tenantIds = entityManager.createQuery(
                "SELECT ut.id.tenantId FROM UserTenant ut WHERE ut.id.userId = :uid",
                String::class.java
            ).setParameter("uid", user.id).resultList

            for (tenantId in tenantIds) {
                for (table in tenantScopedTables) {
                    entityManager.createNativeQuery("DELETE FROM $table WHERE tenant_id = :tid")
                        .setParameter("tid", tenantId)
                        .executeUpdate()
                }
                entityManager.createNativeQuery("DELETE FROM tenants WHERE id = :tid")
                    .setParameter("tid", tenantId)
                    .executeUpdate()
            }
            userRepository.delete(user)
        }
    }
}
