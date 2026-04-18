package com.provisions.calculator.repository

import com.provisions.calculator.model.Tenant
import org.springframework.data.jpa.repository.JpaRepository

interface TenantRepository : JpaRepository<Tenant, String>
