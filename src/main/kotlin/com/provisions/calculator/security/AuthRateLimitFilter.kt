package com.provisions.calculator.security

import tools.jackson.databind.json.JsonMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpMethod
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.concurrent.ConcurrentHashMap

@Component
class AuthRateLimitFilter(private val objectMapper: JsonMapper) : OncePerRequestFilter() {

    @Value("\${app.rate-limit.enabled:true}")
    private var rateLimitEnabled: Boolean = true

    private data class WindowKey(val ip: String, val path: String)

    private val windows = ConcurrentHashMap<WindowKey, ArrayDeque<Long>>()

    private val limits = mapOf(
        "/api/auth/login" to 10,
        "/api/auth/register" to 5,
        "/api/auth/demo" to 5
    )
    private val windowMs = 60_000L

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (!rateLimitEnabled) {
            filterChain.doFilter(request, response)
            return
        }

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
                        mapOf("status" to 429, "message" to "Too many requests. Please wait a minute.")
                    )
                )
                return
            }
        }

        filterChain.doFilter(request, response)
    }

    @Scheduled(fixedDelay = 300_000)
    fun evictExpiredWindows() {
        val cutoff = System.currentTimeMillis() - windowMs
        windows.entries.removeIf { (_, timestamps) ->
            synchronized(timestamps) {
                while (timestamps.isNotEmpty() && timestamps.first() < cutoff) timestamps.removeFirst()
                timestamps.isEmpty()
            }
        }
    }
}
