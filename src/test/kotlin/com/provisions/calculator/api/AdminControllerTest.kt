package com.provisions.calculator.api

import com.provisions.calculator.service.AuthService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import com.provisions.calculator.MockMvcTestConfig
import org.springframework.context.annotation.Import
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@Import(MockMvcTestConfig::class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WithMockUser(roles = ["ADMIN"])
class AdminControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var authService: AuthService

    private var testUserId: Long = 0
    private var testTenantId: String = ""

    private val regularUser: UserDetails = User.withUsername("regular@test.com")
        .password("pass")
        .roles("USER")
        .build()

    @BeforeEach
    fun setup() {
        val response = authService.register("user@test.com", "password1234", "Test User")
        testUserId = response.user.userId
        testTenantId = response.user.tenantIds.first()
    }

    // ---- listUsers ----

    @Test
    fun `listUsers - returns all registered users`() {
        mockMvc.perform(get("/api/admin/users"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].email").value("user@test.com"))
            .andExpect(jsonPath("$[0].status").value("PENDING"))
    }

    @Test
    fun `listUsers - non-admin returns 403`() {
        mockMvc.perform(get("/api/admin/users").with(user(regularUser)))
            .andExpect(status().isForbidden)
    }

    // ---- activateUser ----

    @Test
    fun `activateUser - sets status to ACTIVE`() {
        mockMvc.perform(post("/api/admin/users/$testUserId/activate"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(testUserId))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
    }

    @Test
    fun `activateUser - unknown user returns 404`() {
        mockMvc.perform(post("/api/admin/users/99999/activate"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `activateUser - non-admin returns 403`() {
        mockMvc.perform(post("/api/admin/users/$testUserId/activate").with(user(regularUser)))
            .andExpect(status().isForbidden)
    }

    // ---- disableUser ----

    @Test
    fun `disableUser - sets status to DISABLED`() {
        mockMvc.perform(post("/api/admin/users/$testUserId/disable"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(testUserId))
            .andExpect(jsonPath("$.status").value("DISABLED"))
    }

    @Test
    fun `disableUser - unknown user returns 404`() {
        mockMvc.perform(post("/api/admin/users/99999/disable"))
            .andExpect(status().isNotFound)
    }

    // ---- listTenants ----

    @Test
    fun `listTenants - returns personal tenant of registered user`() {
        mockMvc.perform(get("/api/admin/tenants"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value("test-user"))
    }

    // ---- createTenant ----

    @Test
    fun `createTenant - returns 201 with created tenant`() {
        mockMvc.perform(
            post("/api/admin/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"id":"new-tenant","name":"New Tenant"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value("new-tenant"))
            .andExpect(jsonPath("$.name").value("New Tenant"))
    }

    @Test
    fun `createTenant - blank id returns 400`() {
        mockMvc.perform(
            post("/api/admin/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"id":"","name":"Some Tenant"}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `createTenant - blank name returns 400`() {
        mockMvc.perform(
            post("/api/admin/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"id":"some-id","name":""}""")
        )
            .andExpect(status().isBadRequest)
    }

    // ---- assignTenant ----

    @Test
    fun `assignTenant - assigns extra tenant to user and returns 204`() {
        mockMvc.perform(
            post("/api/admin/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"id":"extra-tenant","name":"Extra"}""")
        )
        mockMvc.perform(post("/api/admin/users/$testUserId/tenants/extra-tenant"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `assignTenant - idempotent double assign both return 204`() {
        mockMvc.perform(
            post("/api/admin/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"id":"idempotent-tenant","name":"Idempotent"}""")
        )
        mockMvc.perform(post("/api/admin/users/$testUserId/tenants/idempotent-tenant"))
            .andExpect(status().isNoContent)
        mockMvc.perform(post("/api/admin/users/$testUserId/tenants/idempotent-tenant"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `assignTenant - unknown user returns 404`() {
        mockMvc.perform(post("/api/admin/users/99999/tenants/test-user"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `assignTenant - unknown tenant returns 404`() {
        mockMvc.perform(post("/api/admin/users/$testUserId/tenants/nonexistent-tenant"))
            .andExpect(status().isNotFound)
    }

    // ---- revokeTenant ----

    @Test
    fun `revokeTenant - removes tenant assignment and returns 204`() {
        mockMvc.perform(delete("/api/admin/users/$testUserId/tenants/$testTenantId"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `revokeTenant - idempotent second revoke returns 204`() {
        mockMvc.perform(delete("/api/admin/users/$testUserId/tenants/$testTenantId"))
            .andExpect(status().isNoContent)
        mockMvc.perform(delete("/api/admin/users/$testUserId/tenants/$testTenantId"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `revokeTenant - non-admin returns 403`() {
        mockMvc.perform(delete("/api/admin/users/$testUserId/tenants/$testTenantId").with(user(regularUser)))
            .andExpect(status().isForbidden)
    }
}
