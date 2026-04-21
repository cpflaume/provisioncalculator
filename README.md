# 🧮 Provision Calculator — Backend

> A REST API for calculating multi-level commission settlements across a customer hierarchy tree.  
> Built with Kotlin and Spring Boot, because Java felt like too much typing.

![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat&logo=kotlin&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_3-6DB33F?style=flat&logo=springboot&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-blue)
![Hosted on](https://img.shields.io/badge/hosted_on-Oracle_Always_Free-F80000?logo=oracle&logoColor=white)
![Coded on](https://img.shields.io/badge/coded_on-mobile_phone-333?logo=android&logoColor=white)

## 🌐 Live Demo

**→ [provisioncalculator.copf-demo.de](https://provisioncalculator.copf-demo.de)**

> A pure JSON API was deemed insufficiently stimulating, so a frontend was built on top. You're welcome.

## Why does it exist?

- Creating it was a purely technical challenge, but I needed some use case to have something to implement 🤓

## ⚙️ What it does

- 🌳 Models a customer hierarchy as a tree (the kind with nodes, not bark)
- 💸 Calculates commissions depth-by-depth — e.g. 10% for direct upline, 5% for grandparent.
- 🔄 Manages the full settlement lifecycle: `configure` → `import` → `calculate` → `approve`
- 📖 Ships with Swagger UI, because documentation should at least be interactive

## 🛠️ Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin — concise, null-safe, quietly judging your Java |
| Framework | Spring Boot 3 |
| Build | Gradle (Kotlin DSL) |
| API Docs | SpringDoc OpenAPI / Swagger UI |
| Reverse Proxy | Caddy — configured in roughly 10 lines |
| Hosting | Oracle Cloud Always Free (yes, actually free, permanently) |

## 🚀 Getting Started

```bash
# Build
./gradlew build

# Run
./gradlew bootRun

# API docs — where you can click buttons and feel productive
open http://localhost:8080/swagger-ui.html
```

Use `acme` as the `tenantId` for testing. See [api-docs.md](api-docs.md) for a full walkthrough.

## 🔗 Related

- Frontend: [provisioncalculator-fe](https://github.com/cpflaume/provisioncalculator-fe) — built because staring at JSON gets old

## 📄 License

MIT — see [LICENSE](LICENSE). Take it. Fork it. Build something less boring.

---

> **Disclaimer:** This entire codebase was written on a mobile phone using [Claude Code](https://claude.ai/code).  
> No laptops were harmed. Thumbs were fine. It was, objectively, good fun.
