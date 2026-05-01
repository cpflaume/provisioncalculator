package com.provisions.calculator.security

import tools.jackson.databind.json.JsonMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties::class)
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val authRateLimitFilter: AuthRateLimitFilter,
    private val objectMapper: JsonMapper,
    @Value("\${app.cors.allowed-origins}") private val allowedOrigins: String
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.allowedOrigins = allowedOrigins.split(",").map { it.trim() }
        config.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        config.allowedHeaders = listOf("Authorization", "Content-Type")
        config.allowCredentials = true
        config.maxAge = 3600L
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint { _: HttpServletRequest, res: HttpServletResponse, _ ->
                    res.status = 401
                    res.contentType = "application/json"
                    res.writer.write(objectMapper.writeValueAsString(mapOf("status" to 401, "message" to "Unauthorized")))
                }
                it.accessDeniedHandler { _: HttpServletRequest, res: HttpServletResponse, _ ->
                    res.status = 403
                    res.contentType = "application/json"
                    res.writer.write(objectMapper.writeValueAsString(mapOf("status" to 403, "message" to "Forbidden")))
                }
            }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                it.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                it.requestMatchers(HttpMethod.POST, "/api/auth/demo").permitAll()
                it.requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                it.requestMatchers("/api/admin/**").hasRole("ADMIN")
                it.requestMatchers("/api/v1/**").authenticated()
                it.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                it.anyRequest().denyAll()
            }
            .addFilterBefore(authRateLimitFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(jwtAuthenticationFilter, AuthRateLimitFilter::class.java)

        return http.build()
    }
}
