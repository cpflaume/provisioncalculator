# Provision Calculator — Backend

A REST API for calculating multi-level commission (provision) settlements across a customer hierarchy tree. Built with Kotlin and Spring Boot.

## What it does

- Models a customer hierarchy as a tree
- Calculates commissions depth-by-depth when a purchase occurs (e.g. 10% for direct upline, 5% for grandparent, etc.)
- Manages the full lifecycle of a settlement: configure → import purchases → calculate → approve
- Exposes interactive API docs via Swagger UI

## Tech stack

- **Kotlin** + **Spring Boot 3**
- **Gradle** (Kotlin DSL)
- **SpringDoc OpenAPI** for Swagger UI
- **Caddy** as reverse proxy

## Getting started

```bash
# Build
./gradlew build

# Run
./gradlew bootRun

# API docs (Swagger UI)
open http://localhost:8080/swagger-ui.html
```

Use `acme` as the `tenantId` for testing. See [api-docs.md](api-docs.md) for a full walkthrough.

## Related

- Frontend: [provisioncalculator-fe](https://github.com/cpflaume/provisioncalculator-fe)

## License

MIT — see [LICENSE](LICENSE)
