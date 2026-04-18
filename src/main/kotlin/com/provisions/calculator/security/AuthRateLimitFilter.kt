package com.provisions.calculator.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.concurrent.ConcurrentHashMap

@Component
class AuthRateLimitFilter(private val objectMapper: ObjectMapper) : OncePerRequestFilter() {

    private data class WindowKey(val ip: String, val path: String)

    private val windows = ConcurrentHashMap<WindowKey, ArrayDeque<Long>>()

    private val limits = mapOf(
        "/api/auth/login" to 10,
        "/api/auth/register" to 5
    )
    private val windowMs = 60_000L

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val path = request.requestURI
        val limit = if (request.method == HttpMethod.POST.name()) limits[path] else null

        if (limit != null) {
            val ip = request.remoteAddr ?: "unknown"
            val key = WindowKey(ip, path)
            val now = System.currentTimeMillis()
            val timestamps = windows.computeIfAbsent(key) { ArrayDeque() }

            val allowed = synchronized(timestamps) {
                val cutoff = now - windowMs
                while (timestamps.isNotEmpty() && timestamps.first() < cutoff) {
                    timestamps.removeFirst()
                }
                if (timestamps.size >= limit) {
                    false
                } else {
                    timestamps.addLast(now)
                    true
                }
            }

            if (!allowed) {
                response.status = 429
                response.contentType = "application/json"
                response.writer.write(
                    objectMapper.writeValueAsString(
                        mapOf("status" to 429, "message" to "Zu viele Anfragen. Bitte warten Sie eine Minute.")
                    )
                )
                return
            }
        }

        filterChain.doFilter(request, response)
    }
}
