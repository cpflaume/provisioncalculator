package com.provisions.calculator

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@TestConfiguration
class MockMvcTestConfig {

    @Bean
    fun mockMvc(context: WebApplicationContext): MockMvc {
        val builder = MockMvcBuilders.webAppContextSetup(context)
        builder.apply<DefaultMockMvcBuilder>(springSecurity())
        return builder.build()
    }
}
