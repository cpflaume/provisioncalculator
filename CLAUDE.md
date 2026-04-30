# CLAUDE.md — Development Guidelines

## Verification Before Pushing

Always run this chain locally before every push. Fix all failures before committing.

```bash
# Run all tests (excludes performance tag)
./gradlew test

# Full build (compiles + tests)
./gradlew build
```

Do not push if any step fails.

## Commit and Push Policy

- Commit after every logical unit of work — do not accumulate unrelated changes in one commit.
- Push to the feature branch immediately after committing.
- Use the branch specified in the task or session. Never push to `main` directly.
- Commit message format: `type: short description` (e.g. `feat: add endpoint for team provisions`, `fix: null check in calculator service`).

## Autonomous Operation (Token Efficiency)

Operate autonomously. The following do **not** require user confirmation:

- Running `./gradlew test`, `./gradlew build`, or any read-only Gradle task.
- Committing and pushing to the designated feature branch.
- Reading any file in the repository.

Only pause and ask when:
- The task description is genuinely ambiguous.
- A destructive or irreversible action is required (force-push, delete branch, drop/migrate production data).
- A decision has significant architectural impact not covered by the existing codebase patterns.

## Code Style

- Match existing Kotlin conventions in the codebase.
- No comments unless the reason is non-obvious.
- No plan files, progress docs, or TODO markdown files — use the conversation instead.
- Prefer editing existing files over creating new ones.
- Database migrations go in `src/main/resources/db/migration` following the Flyway versioning scheme already in use.

## Stack Reference

| Concern | Tool |
|---|---|
| Language | Kotlin (JVM 21) |
| Framework | Spring Boot 4 |
| Persistence | Spring Data JPA + PostgreSQL |
| Migrations | Flyway |
| Security | Spring Security + JWT (jjwt) |
| API docs | SpringDoc OpenAPI (Swagger UI at `/swagger-ui.html`) |
| Tests | JUnit 5, MockK, SpringMockK, TestContainers |
| Build | Gradle (Kotlin DSL) |

Use H2 in-memory DB for unit/integration tests; TestContainers spins up a real PostgreSQL for integration tests tagged accordingly.
