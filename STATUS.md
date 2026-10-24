# DeepToDo — Status

> `PROJECT_SPEC.md` target architecture/scope'u gösterir.
> Bu dosya repository'nin şu anki gerçek durumunu gösterir.
> Coherent implementation task tamamlandığında güncellenir.

## Last updated

2026-10-24

## Current phase

**Keycloak'a geçiş (ROADMAP A4 → K1–K5)**

Karar (2026-10-24): kimlik yönetimi Keycloak'a devredildi (spec v4 §10). Uygulamada yazılmış register / login / JWT üretimi / refresh token / rate limit kodu A4'te kaldırılır; uygulama yalnız OAuth2 Resource Server olur.

## Active task

_None._ Sıradaki: **K1** — Keycloak'ı local'e kur.

Ara durum (A4 sonrası, K3'e kadar): uygulama açılır, token'sız istek 401 döner; token'lı istekler Keycloak (K1) ve yerel kullanıcı eşlemesi (K3) gelene kadar çalışmaz. Keycloak kapalıyken token'lı istek **500** döner (`JwtDecoderInitializationException`) — K2'de ele alınır.

Not: DT-003'te yazılan ve elle doğrulanan refresh token akışı (opaque token, SHA-256 hash, rotation, katı reuse detection) git geçmişinde duruyor (`679ece5`). K5'te Keycloak'ın aynı işi nasıl yaptığıyla karşılaştırılacak.

Commit edilmemiş iş (çalışma dizininde): `users/me` endpoint'leri, workspace + membership + yetki kuralları, V4 `workspace_members`. ROADMAP K4 / W1 / W2'de Keycloak'a uyarlanarak yerleşir.

## Phase status

| Phase | Status |
|---|---|
| Phase 0 — Bootstrap | ✅ Done |
| Phase 1 — Todo Core | ⏸ Paused (users schema done, workspace entity pending) |
| Phase 2 — Workspace / Collaboration / Querying | ⬜ |
| Phase 3 — Security | 🔄 Keycloak'a geçiş (kendi auth kodu kaldırılıyor) |
| Phase 4 — Testing + Production + Deployment | ⬜ |

## Completed tasks

### A4 — Kendi authentication kodunu kaldır ✅ (2026-10-24)

- Silindi: `AuthService`, `AuthTokens`, `AppUserDetails(Service)`, `auth/jwt` (token üretimi, RSA key yükleme, decoder), `auth/refresh` (rotation, reuse), `AuthController` + DTO'lar, `AuthExceptionHandler`, `RefreshTokenCookies`, `EmailAlreadyExist` / `InvalidRefreshToken` exception'ları; `SecurityConfig`'ten `PasswordEncoder`, `AuthenticationManager`, `BearerTokenResolver`, özel `JwtAuthenticationConverter`
- `application.yaml`: `deeptodo.security.*` (JWT key yolları, refresh cookie) yerine `spring.security.oauth2.resourceserver.jwt.issuer-uri` (`KEYCLOAK_ISSUER_URI`, varsayılan `http://localhost:8180/realms/deeptodo`)
- `User`: `passwordHash` alanı çıktı; `users.password_hash` kolonu ve `refresh_tokens` tablosu K3 migration'ına kadar DB'de duruyor (uygulanmış migration değiştirilmez)
- Manual smoke ✅: uygulama açılıyor, health UP; token yok → 401 problem+json; eski `/api/auth/login` → 401 (artık korumalı bir bilinmeyen URL); Keycloak kapalıyken token'lı istek → 500 (beklenen ara durum)

### A2 — Hata sözleşmesi ✅ (2026-10-23)

- Filter zinciri 401 / 403 → `auth/web/SecurityProblemHandler` (`application/problem+json`; `WWW-Authenticate` header'ı Spring'in Bearer entry point'i yazmaya devam eder)
- Uygulama hataları → `common/error/GlobalExceptionHandler`: `ResourceNotFoundException` 404, `ForbiddenOperationException` 403, `BusinessRuleViolationException` 409 (exception'lar HTTP bilmez, spec §13). İlk kullanıcıları A3 / A5'te gelir
- `spring.mvc.problemdetails.enabled: true`: validation 400, bozuk JSON 400, 405 de ProblemDetail
- Manual smoke ✅: token yok / bozuk token → 401 problem+json + WWW-Authenticate; boş alanlarla register → 400 problem+json; bozuk JSON → 400; `DELETE /api/auth/login` → 405; geçerli token → 200

### DT-001 — Bootstrap baseline ✅ (2026-10-01)

- `compose.yaml`: PostgreSQL `17.6`, named volume `postgres-data`, `pg_isready` healthcheck
- `pom.xml`: data-jpa, PostgreSQL driver (runtime), `spring-boot-starter-flyway` + `flyway-database-postgresql`, actuator
- `application.yaml`: env-overridable datasource (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`), `ddl-auto: validate`, `open-in-view: false`
- `src/main/resources/db/migration/` created
- Manual smoke: app starts, Flyway creates `flyway_schema_history`, `/actuator/health` → `UP`

## Local environment

- PostgreSQL (compose) host'ta `127.0.0.1:5433` → container 5432. Host 5432 bu makinede başka bir projenin (`library-postgres`) Postgres'inde; A1 (2026-10-23) ile taşındı.
- `DB_URL` varsayılanı `jdbc:postgresql://localhost:5433/deeptodo`.

## Applied migrations

| Migration | Purpose |
|---|---|
| V1 | `users` (incl. `password_hash varchar(255)`), `workspaces`; named PKs, no time defaults, `~ '\S'` blank checks |
| V2 | `users.version` kolonu kaldırıldı (spec §21; `@Version` yalnız todos / workspace_members'ta) |
| V3 | `refresh_tokens` (spec §7.9, halef bağlantısı yok) + `user_id`, `family_id` index'leri — Keycloak'a geçişle kullanılmıyor, K3'te drop |

## Current schema

`users`, `workspaces`, `refresh_tokens` (V1–V3).

Entity hiyerarşisi: `BaseEntity` (yalnız `id`, UUID v7) → `AuditableEntity` (`created_at` `updatable=false`, `updated_at`; Spring Data auditing). `@Version` base class'ta değil; ihtiyaç duyan entity (Todo, WorkspaceMember) kendisi tanımlar.

## Package layout

```text
common/config     JpaAuditingConfig, ClockConfig
common/domain     BaseEntity, AuditableEntity
common/error      GlobalExceptionHandler, ResourceNotFoundException, ForbiddenOperationException, BusinessRuleViolationException
auth/             SecurityConfig (resource server)
auth/web          SecurityProblemHandler
user/             UserService
user/domain       User (normalizeEmail), UserStatus, UserRepository, UserExampleListener (örnek)
user/web          UserController (GET /api/users/me — K3/K4'te yeniden yazılacak), dto/MeResponse
```

## Known technical debt

| ID | Debt | Planned phase |
|---|---|---|
| TD-3 | `UserExampleListener` ve `UserRepository`'deki deneme sorguları örnek amaçlı; temizlenecek | Phase 3 |
| TD-12 | Validation 400 cevabı hangi alanın hatalı olduğunu söylemiyor (`detail: Invalid request content.`); beklenmeyen 500'ler hâlâ Boot'un `/error` JSON'u (ProblemDetail değil) | Phase 4 (E2 ile) |

Keycloak kararıyla kapanan borçlar: TD-4 (login rate limit → Keycloak brute force detection), TD-8'in refresh rotation kısmı (son OWNER testi W3'te), TD-9 (refresh cookie Secure → cookie kalmadı), TD-10 (rate limit IP / forward headers → E4'te genel olarak ele alınır).

## Current learning focus

- OAuth2 / OIDC: authorization code + PKCE, token anatomisi, discovery, JWKS
- Keycloak: realm / client / user modeli, realm export, Admin API
- Resource Server: issuer / audience doğrulaması, `sub` → yerel kullanıcı eşleme

## Locked scope decisions

Do not casually reopen these during implementation:

- one generic Workspace type
- roles: OWNER / MEMBER
- multiple OWNER allowed
- last OWNER invariant
- multi-assignee Todo remains
- Todo Project relationship optional
- Tag + explicit TodoTag remains
- TodoAssignee explicit join entity
- register does not auto-create Workspace
- tests mainly Phase 4
- no AuditLog in v1
- identity in Keycloak (no own password / token / refresh code)
- Redis / Kafka only for spec §2.1 learning modules; no microservices
- final v1 includes Docker + public HTTPS deployment
