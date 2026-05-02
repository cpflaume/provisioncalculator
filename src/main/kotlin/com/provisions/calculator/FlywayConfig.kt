package com.provisions.calculator

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FlywayConfig {

    @Bean
    fun repairBeforeMigrate(): FlywayMigrationStrategy = FlywayMigrationStrategy { flyway ->
        flyway.repair()
        flyway.migrate()
    }
}
