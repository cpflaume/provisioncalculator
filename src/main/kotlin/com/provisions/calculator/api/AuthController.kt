package com.provisions.calculator.api

import com.provisions.calculator.security.AppUserDetails
import com.provisions.calculator.service.AuthResponse
import com.provisions.calculator.service.AuthService
import com.provisions.calculator.service.AuthUserResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

data class RegisterRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank @field:Size(min = 8) val password: String,
    @field:NotBlank val displayName: String
)

data class LoginRequest(
    @field:NotBlank val email: String,
    @field:NotBlank val password: String
)

@RestController
@RequestMapping("/api/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody req: RegisterRequest): ResponseEntity<AuthResponse> {
        val response = authService.register(req.email, req.password, req.displayName)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody req: LoginRequest): ResponseEntity<AuthResponse> {
        val response = authService.login(req.email, req.password)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: AppUserDetails): ResponseEntity<AuthUserResponse> {
        return ResponseEntity.ok(
            AuthUserResponse(
                userId = principal.userId,
                email = principal.username,
                displayName = principal.displayName,
                role = principal.role,
                status = principal.status,
                tenantIds = principal.tenantIds
            )
        )
    }
}
