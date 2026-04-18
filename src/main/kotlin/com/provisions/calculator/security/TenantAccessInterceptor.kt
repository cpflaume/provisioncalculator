package com.provisions.calculator.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Component
class TenantAccessInterceptor : HandlerInterceptor {

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        val authentication = SecurityContextHolder.getContext().authentication ?: return true

        // ADMIN bypasses tenant isolation — can access any tenant
        if (authentication.authorities.any { it.authority == "ROLE_ADMIN" }) return true

        val principal = authentication.principal
        if (principal is AppUserDetails) {
            val tenantId = extractTenantId(request.requestURI)
            if (tenantId != null && tenantId !in principal.tenantIds) {
                response.status = HttpServletResponse.SC_FORBIDDEN
                response.contentType = "application/json"
                response.writer.write("""{"status":403,"message":"Access denied to tenant"}""")
                return false
            }
        }
        return true
    }

    private fun extractTenantId(uri: String): String? {
        val match = Regex("/api/v[0-9]+/tenants/([^/]+)").find(uri)
        return match?.groupValues?.get(1)?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) }
    }
}
