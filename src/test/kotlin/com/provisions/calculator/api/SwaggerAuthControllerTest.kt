package com.provisions.calculator.api

import com.provisions.calculator.MockMvcTestConfig
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@Import(MockMvcTestConfig::class)
@ActiveProfiles("test")
class SwaggerAuthControllerTest {

    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `swagger-auth - returns HTML page without authentication`() {
        mockMvc.perform(get("/api/swagger-auth"))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", startsWith("text/html")))
            .andExpect(content().string(containsString("""id="swagger-ui"""")))
            .andExpect(content().string(containsString("""url: '/v3/api-docs'""")))
            .andExpect(content().string(containsString("preauthorizeApiKey")))
    }
}
