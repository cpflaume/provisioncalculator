package com.provisions.calculator.api

import tools.jackson.databind.json.JsonMapper
import com.provisions.calculator.model.UserRole
import com.provisions.calculator.model.UserStatus
import com.provisions.calculator.security.AppUserDetails
import com.provisions.calculator.service.AuthService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import com.provisions.calculator.MockMvcTestConfig
import org.springframework.context.annotation.Import
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
@Import(MockMvcTestConfig::class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: JsonMapper
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
        assert(tenantIds.any { it.asString() == "test-user" }) {
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
            displayName = "Me User",
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

    @Test
    fun `me - response includes authProvider field`() {
        val registered = authService.register("provider@example.com", "password1234", "Provider User")
        val principal = AppUserDetails(
            userId = registered.user.userId,
            email = "provider@example.com",
            displayName = "Provider User",
            hashedPassword = null,
            role = UserRole.USER,
            status = UserStatus.PENDING,
            tenantIds = registered.user.tenantIds,
            authProvider = "LOCAL",
        )

        mockMvc.perform(get("/api/auth/me").with(user(principal)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authProvider").value("LOCAL"))
    }

    // ---- demo ----

    @Test
    fun `demo - returns 201 with ACTIVE USER and DEMO authProvider`() {
        mockMvc.perform(post("/api/auth/demo").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.token").isNotEmpty)
            .andExpect(jsonPath("$.user.authProvider").value("DEMO"))
            .andExpect(jsonPath("$.user.status").value("ACTIVE"))
            .andExpect(jsonPath("$.user.role").value("USER"))
            .andExpect(jsonPath("$.user.displayName").value("Demo"))
    }

    @Test
    fun `demo - response contains exactly one tenantId`() {
        val result = mockMvc.perform(post("/api/auth/demo").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated)
            .andReturn()

        val tenantIds = objectMapper.readTree(result.response.contentAsString)["user"]["tenantIds"]
        assert(tenantIds.size() == 1) { "Expected exactly 1 tenantId, got: $tenantIds" }
        assert(tenantIds[0].asString().startsWith("demo-")) { "Expected tenantId to start with 'demo-', got: ${tenantIds[0]}" }
    }

    @Test
    fun `demo - expiresAt is approximately 24 hours from now`() {
        val before = java.time.Instant.now()
        val result = mockMvc.perform(post("/api/auth/demo").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated)
            .andReturn()

        val expiresAtStr = objectMapper.readTree(result.response.contentAsString)["user"]["expiresAt"].asText()
        val expiresAt = java.time.Instant.parse(expiresAtStr)
        val expectedMin = before.plus(23, java.time.temporal.ChronoUnit.HOURS)
        val expectedMax = before.plus(25, java.time.temporal.ChronoUnit.HOURS)
        assert(expiresAt.isAfter(expectedMin) && expiresAt.isBefore(expectedMax)) {
            "expiresAt $expiresAt not within 23-25h window from $before"
        }
    }

    @Test
    fun `demo - each call creates a distinct user and tenant`() {
        val result1 = mockMvc.perform(post("/api/auth/demo").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated).andReturn()
        val result2 = mockMvc.perform(post("/api/auth/demo").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated).andReturn()

        val user1 = objectMapper.readTree(result1.response.contentAsString)["user"]
        val user2 = objectMapper.readTree(result2.response.contentAsString)["user"]

        assert(user1["userId"].asLong() != user2["userId"].asLong()) { "Expected distinct user IDs" }
        assert(user1["tenantIds"][0].asText() != user2["tenantIds"][0].asText()) { "Expected distinct tenant IDs" }
        assert(user1["email"].asText() != user2["email"].asText()) { "Expected distinct emails" }
    }

    @Test
    fun `demo - unauthenticated access is allowed (no token required)`() {
        mockMvc.perform(post("/api/auth/demo").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated)
    }
}
