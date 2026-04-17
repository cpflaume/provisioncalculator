package com.provisions.calculator.security

import com.provisions.calculator.model.UserRole
import com.provisions.calculator.model.UserStatus
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(private val jwtService: JwtService) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ")) {
            val token = header.removePrefix("Bearer ")
            try {
                val claims = jwtService.parse(token)
                val userId = (claims.get("userId", Integer::class.java)).toLong()
                val email = claims.subject
                val role = UserRole.valueOf(claims.get("role", String::class.java))
                val status = UserStatus.valueOf(claims.get("status", String::class.java))
                @Suppress("UNCHECKED_CAST")
                val tenantIds = (claims.get("tenantIds") as List<String>).toSet()

                val userDetails = AppUserDetails(userId, email, null, role, status, tenantIds)
                val auth = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
                SecurityContextHolder.getContext().authentication = auth
            } catch (_: Exception) {
                // Invalid token — leave context unauthenticated; security rules enforce access
            }
        }
        filterChain.doFilter(request, response)
    }
}
