package com.provisions.calculator

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.tags.Tag
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openAPI(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Provision Calculator API")
                .version("1.0.0")
                .description(
                    """
                    Multi-tenant commission calculation service.

                    Calculate depth-based commissions across referral trees for any settlement period.

                    ## How it works

                    1. **Create a settlement** (billing period)
                    2. **Configure** the referral tree and commission rates per depth level
                    3. **Submit purchases** made by customers in the tree
                    4. **Calculate** commissions — each purchase generates commissions for all ancestors up to the configured depth
                    5. **Approve** the settlement to finalize, or **reject** to go back and make changes

                    ## Multi-Tenancy

                    Every endpoint is scoped by `tenantId` in the URL path. Tenants are fully isolated.
                    Use any string as tenant ID — no registration required.

                    ## Example

                    Given a tree `Alice → Bob → Diana` with rates depth 1 = 10%, depth 2 = 5%:

                    If Diana purchases 1,000.00:
                    - Bob (1 level up) earns 100.00
                    - Alice (2 levels up) earns 50.00
                    """.trimIndent()
                )
                .contact(Contact().name("Provision Calculator"))
        )
        .tags(
            listOf(
                Tag().name("Settlements").description("Create and manage settlement periods"),
                Tag().name("Purchases").description("Submit and list purchases"),
                Tag().name("Calculation").description("Calculate commissions, view results and audit trail")
            )
        )
}
