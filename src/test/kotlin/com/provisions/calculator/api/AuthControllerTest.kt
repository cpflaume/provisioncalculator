package com.provisions.calculator.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.provisions.calculator.model.UserRole
import com.provisions.calculator.model.UserStatus
import com.provisions.calculator.security.AppUserDetails
import com.provisions.calculator.service.AuthService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var authService: AuthService

    private fun registerBody(
        email: String = "test@example.com",
        password: String = "password1234",
        displayName: String = "Test User",
    ) = """{"email":"$email","password":"$password","displayName":"$displayName"}"""

    private fun loginBody(email: String = "test@example.com", password: String = "password1234") =
        """{"email":"$email","password":"$password"}"""

    // ---- register ----

    @Test
    fun `register - valid request returns 201 with PENDING USER and token`() {
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody())
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.token").isNotEmpty)
            .andExpect(jsonPath("$.user.email").value("test@example.com"))
            .andExpect(jsonPath("$.user.displayName").value("Test User"))
            .andExpect(jsonPath("$.user.role").value("USER"))
            .andExpect(jsonPath("$.user.status").value("PENDING"))
    }

    @Test
    fun `register - creates personal tenant slug from displayName`() {
        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody())
        )
            .andExpect(status().isCreated)
            .andReturn()

        val tenantIds = objectMapper.readTree(result.response.contentAsString)["user"]["tenantIds"]
        assert(tenantIds.any { it.asText() == "test-user" }) {
            "Expected tenantIds to contain 'test-user', got: $tenantIds"
        }
    }

    @Test
    fun `register - duplicate email returns 409`() {
        authService.register("test@example.com", "password1234", "Test User")

        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody())
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `register - invalid email format returns 400`() {
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email = "not-an-email"))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `register - password shorter than 8 chars returns 400`() {
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(password = "short"))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `register - blank displayName returns 400`() {
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(displayName = ""))
        )
            .andExpect(status().isBadRequest)
    }

    // ---- login ----

    @Test
    fun `login - valid credentials return 200 with token`() {
        authService.register("test@example.com", "password1234", "Test User")

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").isNotEmpty)
            .andExpect(jsonPath("$.user.email").value("test@example.com"))
    }

    @Test
    fun `login - wrong password returns 401`() {
        authService.register("test@example.com", "password1234", "Test User")

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(password = "wrongpassword"))
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `login - nonexistent email returns 401`() {
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email = "nobody@example.com"))
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `login - blank email returns 400`() {
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email = ""))
        )
            .andExpect(status().isBadRequest)
    }

    // ---- me ----

    @Test
    fun `me - unauthenticated returns 401`() {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `me - authenticated returns current user`() {
        val registered = authService.register("me@example.com", "password1234", "Me User")
        val principal = AppUserDetails(
            userId = registered.user.userId,
            email = "me@example.com",
            hashedPassword = null,
            role = UserRole.USER,
            status = UserStatus.PENDING,
            tenantIds = registered.user.tenantIds,
        )

        mockMvc.perform(get("/api/auth/me").with(user(principal)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(registered.user.userId))
            .andExpect(jsonPath("$.email").value("me@example.com"))
            .andExpect(jsonPath("$.displayName").value("Me User"))
            .andExpect(jsonPath("$.status").value("PENDING"))
    }
}
