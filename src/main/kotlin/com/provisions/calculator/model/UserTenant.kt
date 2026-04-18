package com.provisions.calculator.model

import jakarta.persistence.*
import java.io.Serializable

@Embeddable
data class UserTenantId(
    val userId: Long = 0,
    val tenantId: String = ""
) : Serializable

@Entity
@Table(name = "user_tenants")
class UserTenant(
    @EmbeddedId
    val id: UserTenantId,

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("tenantId")
    @JoinColumn(name = "tenant_id")
    val tenant: Tenant
)
