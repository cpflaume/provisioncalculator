package com.provisions.calculator

import com.provisions.calculator.model.UserRole
import com.provisions.calculator.model.UserStatus
import com.provisions.calculator.repository.UserRepository
import com.provisions.calculator.service.AuthService
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@ConditionalOnProperty(name = ["app.seed.admin.enabled"], havingValue = "true")
@org.springframework.context.annotation.Profile("!prod")
class TestDataSeeder(
    private val authService: AuthService,
    private val userRepository: UserRepository
) : CommandLineRunner {

    @org.springframework.beans.factory.annotation.Value("\${app.seed.admin.email:admin@e2e.test}")
    lateinit var adminEmail: String

    @org.springframework.beans.factory.annotation.Value("\${app.seed.admin.password:Admin1234!}")
    lateinit var adminPassword: String

    @org.springframework.beans.factory.annotation.Value("\${app.seed.admin.display-name:E2E Admin}")
    lateinit var adminDisplayName: String

    @Transactional
    override fun run(vararg args: String) {
        if (!userRepository.existsByEmail(adminEmail)) {
            authService.register(adminEmail, adminPassword, adminDisplayName)
            val user = userRepository.findByEmail(adminEmail).orElseThrow()
            user.role = UserRole.ADMIN
            user.status = UserStatus.ACTIVE
            userRepository.save(user)
        }
    }
}
