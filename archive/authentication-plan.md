# Authentifizierung & Nutzerverwaltung — Implementierungsplan

## Context

Die App läuft öffentlich im Internet als Demo. Aktuell gibt es keine Authentifizierung — alle API-Endpunkte sind offen. Ziel: kontrollierter Zugang mit Admin-Freischaltung, Rollentrennung (ADMIN/USER) und Mandanten-Isolation.

---

## Datenbankschema (V8 — neue Flyway-Migration)

```sql
CREATE TABLE users (
    id              BIGSERIAL       PRIMARY KEY,
    email           VARCHAR(255)    NOT NULL UNIQUE,
    password_hash   VARCHAR(255),                    -- nullable: SSO-User haben kein lokales Passwort
    password_salt   VARCHAR(255),                    -- nullable: für zukünftige Algorithmus-Wechsel (BCrypt hat Salt eingebettet)
    display_name    VARCHAR(255)    NOT NULL,
    role            VARCHAR(50)     NOT NULL DEFAULT 'USER',
    status          VARCHAR(50)     NOT NULL DEFAULT 'PENDING',
    auth_provider   VARCHAR(50)     NOT NULL DEFAULT 'LOCAL', -- SSO-Readiness
    provider_id     VARCHAR(255),                    -- nullable: SSO-Identifier (z.B. Google sub)
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE tenants (
    id              VARCHAR(255)    PRIMARY KEY,     -- Slug, z.B. "john-doe-a1b2"
    name            VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE user_tenants (
    user_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tenant_id       VARCHAR(255)    NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, tenant_id)
);

CREATE INDEX idx_users_email      ON users (email);
CREATE INDEX idx_user_tenants_uid ON user_tenants (user_id);
CREATE INDEX idx_user_tenants_tid ON user_tenants (tenant_id);
```

**Hinweis Salt:** BCrypt bettet den Salt bereits im Hash-String ein (`$2a$12$...`). Die `password_salt`-Spalte ist für einen möglichen späteren Wechsel zu PBKDF2/Argon2 vorgesehen oder für externe Systeme, die Salts separat erwarten. Bei reiner BCrypt-Nutzung bleibt sie `NULL`.

**SSO-Readiness:** `password_hash`/`password_salt` nullable + `auth_provider`/`provider_id` — reicht später für einen `SsoCallbackController`, der denselben `JwtService.generate()` aufruft. Keine weiteren Security-Änderungen nötig.

---

## Backend — neue Dateien

### Security-Layer (`security/`)
| Datei | Zweck |
|---|---|
| `JwtProperties.kt` | Config-Properties `app.jwt.secret`, `expirationMs`, `issuer` |
| `JwtService.kt` | Token generieren + validieren → gibt `JwtClaims(sub, userId, role, tenantIds, status)` zurück |
| `AppUserDetails.kt` | `UserDetails`-Impl. mit `userId`, `tenantIds`, `status` — kein extra DB-Hit nötig |
| `JwtAuthenticationFilter.kt` | `OncePerRequestFilter`: liest Bearer-Token, setzt `SecurityContext` |
| `SecurityConfig.kt` | Stateless, CSRF off, Filter-Chain + JSON `AuthEntryPoint`/`AccessDeniedHandler` |
| `TenantAccessInterceptor.kt` | `HandlerInterceptor` für `/api/v1/**`: prüft `{tenantId}` gegen `principal.tenantIds` |
| `WebMvcConfig.kt` | Registriert `TenantAccessInterceptor` für `/api/v1/**` |

**SecurityFilterChain-Regeln:**
```
POST /api/auth/register   → permitAll
POST /api/auth/login      → permitAll
GET  /api/auth/me         → permitAll (JWT-Filter liefert trotzdem 401 ohne Token)
/api/admin/**             → hasRole("ADMIN")
/api/v1/**                → authenticated (+ TenantAccessInterceptor)
alles andere              → deny
```

### Domain (`model/`)
- `User.kt`, `UserRole.kt` (ADMIN/USER), `UserStatus.kt` (PENDING/ACTIVE/DISABLED)
- `Tenant.kt` (natürlicher PK: Slug-String)
- `UserTenant.kt` (Composite Key via `@EmbeddedId`)

### Repositories (`repository/`)
- `UserRepository.kt` — `findByEmail()`, `existsByEmail()`
- `TenantRepository.kt`
- `UserTenantRepository.kt` — `findByUserId()`, `deleteByUserIdAndTenantId()`

### Services (`service/`)
- `SlugService.kt` — `slugify(displayName)`: lowercase, Leerzeichen→`-`, bei Konflikt 4-char-Suffix
- `AuthService.kt` — `register()`: User anlegen (PENDING) + persönlichen Mandant erstellen; `login()`: Credentials prüfen + JWT generieren
- `UserAdminService.kt` — CRUD für User-Verwaltung + Mandanten-Zuweisung

### Controller (`api/`)
- `AuthController.kt` — `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me`
- `AdminController.kt` — alle `/api/admin/**`-Endpunkte

---

## API-Vertrag

### Auth
```
POST /api/auth/register
Body: { email, password, displayName }
201:  { token, user: { userId, email, displayName, role, status, tenantIds } }
409:  E-Mail bereits vergeben

POST /api/auth/login
Body: { email, password }
200:  { token, user: { ... } }
401:  Ungültige Credentials

GET /api/auth/me   (Bearer Token)
200:  { userId, email, displayName, role, status, tenantIds }
```

### Admin
```
GET    /api/admin/users
POST   /api/admin/users/{userId}/activate
POST   /api/admin/users/{userId}/disable
GET    /api/admin/tenants
POST   /api/admin/tenants                         Body: { id, name }
POST   /api/admin/users/{userId}/tenants/{tid}    Mandant zuweisen (idempotent, 204)
DELETE /api/admin/users/{userId}/tenants/{tid}    Mandant entziehen (204)
```

### Persönlicher Mandant
Beim Register wird automatisch ein Mandant mit Slug = `slugify(displayName)` angelegt und dem User zugewiesen.

---

## Backend — geänderte Dateien

**`build.gradle.kts`**
```kotlin
implementation("org.springframework.boot:spring-boot-starter-security")
implementation("io.jsonwebtoken:jjwt-api:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
```

**`application.yml`**
```yaml
app:
  jwt:
    secret: ${JWT_SECRET:dev-secret-replace-in-production-256bit}
    expiration-ms: 86400000
    issuer: provisioncalculator
```

**`OpenApiConfig.kt`** — Bearer-SecurityScheme hinzufügen (Swagger "Authorize"-Button)

**`GlobalExceptionHandler.kt`** — keine Änderung nötig; 401/403-Responses werden direkt in `SecurityConfig` über `JsonAuthEntryPoint`/`JsonAccessDeniedHandler` geschrieben

**Bestehende Integrationstests** — `@BeforeEach`: Test-Admin registrieren + einloggen, JWT zu allen MockMvc-Requests hinzufügen

---

## Frontend — neue Dateien

| Datei | Zweck |
|---|---|
| `src/context/AuthContext.tsx` | `createContext` + `AuthProvider` — hält `user`, `token` |
| `src/hooks/useAuth.ts` | Login/Register/Logout, localStorage-Persistenz, `GET /api/auth/me` on mount |
| `src/api/auth.ts` | `login()`, `register()`, `getMe()` — direkte fetch-Calls (kein Tenant-Scope) |
| `src/api/admin.ts` | Alle Admin-API-Calls |
| `src/hooks/useAdminData.ts` | TanStack Query Wrapper für Admin-API |
| `src/components/auth/RequireAuth.tsx` | Guard: kein Token → `/login`; PENDING → `/pending`; sonst `<Outlet />` |
| `src/components/auth/RequireAdmin.tsx` | Guard: kein ADMIN → `/` |
| `src/pages/LoginPage.tsx` | E-Mail + Passwort Form |
| `src/pages/RegisterPage.tsx` | Display Name + E-Mail + Passwort Form |
| `src/pages/PendingApprovalPage.tsx` | Freundliche Meldung "Warte auf Freischaltung durch Admin" + Logout |
| `src/pages/AdminPage.tsx` | Tabs: Benutzer (aktivieren/deaktivieren) + Mandanten (anlegen/zuweisen) |

---

## Frontend — geänderte Dateien

**`src/api/client.ts`**
- `getAuthHeader()`: liest JWT aus localStorage → `{ Authorization: "Bearer ..." }`
- `apiGet/Post/Put`: Auth-Header injizieren
- `401`-Response: dispatcht `"auth-expired"` Custom Event → AuthProvider löscht State + redirect `/login`

**`src/App.tsx`**
- `AuthProvider` wrappen
- Hardcoded `tenantId = "acme"` entfernen → kommt aus `useAuth().user.tenantIds[0]`
- Neue Routen-Struktur:
```tsx
<Route path="/login"    element={<LoginPage />} />
<Route path="/register" element={<RegisterPage />} />
<Route path="/pending"  element={<PendingApprovalPage />} />
<Route element={<RequireAuth />}>
  <Route path="/"                element={<DashboardPage />} />
  <Route path="/settlements/:id" element={<SettlementPage />} />
  <Route element={<RequireAdmin />}>
    <Route path="/admin" element={<AdminPage />} />
  </Route>
</Route>
```

**`src/hooks/useTenant.ts`** — `tenantId` initial aus `useAuth().user?.tenantIds[0]`

**`src/components/layout/TenantSelector.tsx`** — Dropdown mit `user.tenantIds` statt freiem Text-Input

**`src/components/layout/Sidebar.tsx`**
- Logout-Button (unten)
- "Admin"-Link wenn `user.role === "ADMIN"`

---

## Implementierungs-Reihenfolge

1. **DB** — `V8__add_user_management.sql` schreiben + Flyway prüfen
2. **Enums + Entities** — `UserRole`, `UserStatus`, `User`, `Tenant`, `UserTenant`
3. **Repositories** — `UserRepository`, `TenantRepository`, `UserTenantRepository`
4. **Dependencies** — Security + JJWT in `build.gradle.kts`
5. **Security-Infrastruktur** — `JwtProperties`, `JwtService` (mit Unit-Test), `AppUserDetails`, `JwtAuthenticationFilter`, `SecurityConfig` (zunächst permissive, dann verschärfen)
6. **Auth-Service + Controller** — `SlugService`, `AuthService`, `AuthController`
7. **Tenant-Interceptor** — `TenantAccessInterceptor` + `WebMvcConfig`
8. **Admin** — `UserAdminService`, `AdminController`
9. **OpenAPI** — Bearer Scheme
10. **Bestehende Tests anpassen** — JWT in MockMvc
11. **Frontend Auth-Foundation** — `AuthContext`, `useAuth`, `client.ts`-Update, `auth.ts`
12. **Login/Register/Pending-Pages** + Route-Guards
13. **Tenant-Selector** aus JWT verdrahten
14. **Admin-Page** — `admin.ts`, `useAdminData`, `AdminPage`
15. **Sidebar** — Logout + Admin-Link
16. **E2E Tests** (Playwright)

---

## E2E Tests (Playwright — `provisioncalculator-fe/e2e/`)

Neue Testdatei: `e2e/auth.spec.ts`

### TC-01: Registrierung + Pending-Status
```
1. Navigiere zu /register
2. Fülle Formular aus (Name, E-Mail, Passwort)
3. Sende ab
4. Erwarte Weiterleitung auf /pending
5. Prüfe: Seite zeigt "Warte auf Freischaltung" + Display Name des Users
6. Prüfe: Navigation zu / leitet zurück auf /pending (Guard aktiv)
```

### TC-02: Login ohne Account → Fehler
```
1. Navigiere zu /login
2. Sende ungültige Credentials ab
3. Erwarte: Fehlermeldung sichtbar, kein Redirect
```

### TC-03: Admin aktiviert User → Zugang freigeschaltet
```
1. Admin-User einloggen (Seed-Daten oder vorheriger Test)
2. Navigiere zu /admin → Tab "Benutzer"
3. Klicke "Aktivieren" bei pending User aus TC-01
4. Als pending User: GET /api/auth/me aufrufen → status = ACTIVE
5. Als aktivierter User erneut einloggen
6. Erwarte Weiterleitung auf / (Dashboard sichtbar)
```

### TC-04: Mandanten-Isolation
```
1. Aktiver User A (Mandant: "user-a") einloggen
2. API-Call auf /api/v1/tenants/user-a/settlements → 200
3. API-Call auf /api/v1/tenants/user-b/settlements → 403
4. Prüfe: TenantSelector zeigt nur "user-a"
```

### TC-05: Admin-Bereich gesperrt für normale User
```
1. Normaler aktiver User einloggen
2. Navigiere zu /admin → Weiterleitung auf /
3. GET /api/admin/users mit User-Token → 403
```

### TC-06: Logout
```
1. Aktiver User einloggen
2. Klicke Logout in der Sidebar
3. Erwarte Weiterleitung auf /login
4. Navigiere zurück zu / → Weiterleitung auf /login (Token gelöscht)
```

### TC-07: Token-Ablauf
```
1. Manipuliere localStorage: abgelaufenen Token setzen
2. Navigiere zu /
3. Erwarte automatische Weiterleitung auf /login
```

### Hilfsfunktionen (`e2e/helpers/auth.ts`)
```typescript
export async function registerUser(page, { name, email, password })
export async function loginAs(page, { email, password })
export async function loginAsAdmin(page)              // nutzt Seed-Admin
export async function activateUser(page, email)       // Admin aktiviert User
```

---

## Kritische Dateien

- `provisioncalculator/build.gradle.kts`
- `provisioncalculator/src/main/resources/application.yml`
- `provisioncalculator/src/main/kotlin/com/provisions/calculator/GlobalExceptionHandler.kt`
- `provisioncalculator/src/main/kotlin/com/provisions/calculator/OpenApiConfig.kt`
- `provisioncalculator-fe/src/api/client.ts`
- `provisioncalculator-fe/src/App.tsx`
- `provisioncalculator-fe/src/hooks/useTenant.ts`
- `provisioncalculator-fe/src/components/layout/TenantSelector.tsx`
- `provisioncalculator-fe/src/components/layout/Sidebar.tsx`
- `provisioncalculator-fe/e2e/auth.spec.ts` (neu)
- `provisioncalculator-fe/e2e/helpers/auth.ts` (neu)
