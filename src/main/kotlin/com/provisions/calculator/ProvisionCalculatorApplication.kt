package com.provisions.calculator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class ProvisionCalculatorApplication

fun main(args: Array<String>) {
    runApplication<ProvisionCalculatorApplication>(*args)
}
