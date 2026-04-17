package com.provisions.calculator.api

import com.provisions.calculator.service.TenantView
import com.provisions.calculator.service.UserAdminService
import com.provisions.calculator.service.UserAdminView
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class CreateTenantRequest(
    @field:NotBlank val id: String,
    @field:NotBlank val name: String
)

@RestController
@RequestMapping("/api/admin")
class AdminController(private val userAdminService: UserAdminService) {

    @GetMapping("/users")
    fun listUsers(): List<UserAdminView> = userAdminService.listUsers()

    @PostMapping("/users/{userId}/activate")
    fun activateUser(@PathVariable userId: Long): UserAdminView = userAdminService.activate(userId)

    @PostMapping("/users/{userId}/disable")
    fun disableUser(@PathVariable userId: Long): UserAdminView = userAdminService.disable(userId)

    @GetMapping("/tenants")
    fun listTenants(): List<TenantView> = userAdminService.listTenants()

    @PostMapping("/tenants")
    fun createTenant(@Valid @RequestBody req: CreateTenantRequest): ResponseEntity<TenantView> {
        val tenant = userAdminService.createTenant(req.id, req.name)
        return ResponseEntity.status(201).body(tenant)
    }

    @PostMapping("/users/{userId}/tenants/{tenantId}")
    fun assignTenant(@PathVariable userId: Long, @PathVariable tenantId: String): ResponseEntity<Void> {
        userAdminService.assignTenant(userId, tenantId)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/users/{userId}/tenants/{tenantId}")
    fun revokeTenant(@PathVariable userId: Long, @PathVariable tenantId: String): ResponseEntity<Void> {
        userAdminService.revokeTenant(userId, tenantId)
        return ResponseEntity.noContent().build()
    }
}
