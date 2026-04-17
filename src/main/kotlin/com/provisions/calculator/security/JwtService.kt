package com.provisions.calculator.security

import com.provisions.calculator.model.UserRole
import com.provisions.calculator.model.UserStatus
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.util.Date
import javax.crypto.SecretKey

@Service
class JwtService(private val props: JwtProperties) {

    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(props.secret.toByteArray(Charsets.UTF_8))
    }

    fun generate(userId: Long, email: String, role: UserRole, status: UserStatus, tenantIds: Set<String>): String {
        val now = System.currentTimeMillis()
        return Jwts.builder()
            .issuer(props.issuer)
            .subject(email)
            .claim("userId", userId)
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
