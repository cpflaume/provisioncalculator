package com.provisions.calculator.security

import com.provisions.calculator.model.UserRole
import com.provisions.calculator.model.UserStatus
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import java.util.Date
import javax.crypto.SecretKey

@Service
class JwtService(private val props: JwtProperties) {

    @PostConstruct
    fun validateSecret() {
        val bytes = props.secret.toByteArray(Charsets.UTF_8)
        require(bytes.size >= 32) {
            "JWT secret must be at least 32 bytes (256 bits). Set the JWT_SECRET environment variable."
        }
    }

    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(props.secret.toByteArray(Charsets.UTF_8))
    }

    fun generate(userId: Long, email: String, displayName: String, role: UserRole, status: UserStatus, tenantIds: Set<String>): String {
        val now = System.currentTimeMillis()
        return Jwts.builder()
            .issuer(props.issuer)
            .subject(email)
            .claim("userId", userId)
            .claim("displayName", displayName)
            .claim("role", role.name)
            .claim("status", status.name)
            .claim("tenantIds", tenantIds.toList())
            .issuedAt(Date(now))
            .expiration(Date(now + props.expirationMs))
            .signWith(key)
            .compact()
    }

    fun parse(token: String): Claims =
        Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
}
