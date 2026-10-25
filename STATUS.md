# DeepToDo — Status

> `PROJECT_SPEC.md` target architecture/scope'u gösterir.
> Bu dosya repository'nin şu anki gerçek durumunu gösterir.
> Coherent implementation task tamamlandığında güncellenir.

## Last updated

2026-10-25

## Current phase

**Phase 3 — Authentication'ı sıfırdan yeniden kurma (ROADMAP A4 → R1–R11)**

Karar (2026-10-25): Keycloak denemesi (2026-10-24, K1–K2) bırakıldı; authentication uygulamada, Spring Security ile, küçük adımlarla sıfırdan kurulur (spec v5 §10). Gerekçe: öğrenme hedefi Spring Security'nin içi.

## Active task

_None._ Sıradaki: **R1** — Spring Security'yi ekle, kendi `SecurityFilterChain`'imiz.

Ara durum (A4 sonrası, R1'e kadar): uygulamada security yok; bütün endpoint'ler açık (şu an domain endpoint'i de yok). Health UP.

Referanslar: önceki kendi auth implementasyonu `679ece5` (refresh rotation, reuse detection); auth'tan önce yazılmış commit edilmemiş user / workspace kodu `wip/user-workspace-reference` branch'inde (W1–W2 ve R10'da referans).

## Phase status

| Phase | Status |
|---|---|
| Phase 0 — Bootstrap | ✅ Done |
| Phase 1 — Todo Core | ⏸ Paused (users schema done, workspace entity pending) |
| Phase 2 — Workspace / Collaboration / Querying | ⬜ |
| Phase 3 — Security | 🔄 Authentication sıfırdan yeniden kuruluyor (R1–R11) |
| Phase 4 — Testing + Production + Deployment | ⬜ |

## Completed tasks

### A5 — Commit bazlı çalışma akışı ✅ (2026-10-25)

- PR akışı kaldırıldı: her adım `develop`'a doğrudan tek commit. Adım bitince commit edilmeden bekler (sağ paneldeki diff'te görünsün diye); Onat'ın bir sonraki "devam"ında commit + push (`CLAUDE.md` → "Commit akışı")

### A4 — Auth sıfırlama ✅ (2026-10-25)

- Kaldırıldı: Keycloak (compose servisi, `keycloak/realm-deeptodo.json`, `docker/postgres-init`, local `keycloak` database'i ve rolü), `auth` paketinin tamamı (`SecurityConfig`, `SecurityProblemHandler`), `spring-boot-starter-security`, `spring-boot-starter-security-oauth2-resource-server`, `spring-security-test`, `spring.security.*` ayarı, `UserController` (`/api/users/me`) ve `UserService`
- Kaldı: DB şeması (V1–V3; `users.password_hash`, `refresh_tokens` R adımlarında kullanılacak), `common/error`, `User` entity'si
- Manual smoke ✅: uygulama açılıyor, Flyway 4 migration'ı doğruladı (local DB'deki V4 önceki testlerden), health UP, `/api/users/me` → 404 ProblemDetail (security yok, endpoint yok)

### K2 — Resource server sıkılaştırma ✅ (2026-10-25) — bırakılan Keycloak yolu, A4'te kaldırılır

- Realm: `deeptodo-api` client'ı (token istemez; yalnız audience adı) + `deeptodo-web` / `deeptodo-dev`'de `oidc-audience-mapper` → access token'da `aud: deeptodo-api`
- `application.yaml`: `spring.security.oauth2.resourceserver.jwt.audiences: deeptodo-api` (`KEYCLOAK_AUDIENCE`) → Boot, varsayılan validator'lara (exp/nbf, iss) audience validator'ı ekler
- Keycloak'a ulaşılamadığı için token hakkında karar verilemezse (JWKS indirilemez) 503 + `Retry-After: 30` ProblemDetail: `BearerTokenAuthenticationFilter`'ın failure handler'ı `AuthenticationServiceException`'ı yeniden fırlatmak yerine `SecurityProblemHandler`'a yollar
- Manual smoke ✅: `aud`'suz token → 401 `The aud claim is not valid`; realm güncellendikten sonra 404 (geçti); key'ler alındıktan sonra Keycloak kapalı → geçerli token geçer; Keycloak kapalıyken tanınmayan `kid` → 503 + `Retry-After`; uygulama Keycloak'ı hiç görmeden kapalıysa → 500, Keycloak gelince kendiliğinden düzelir (TD-13)
- Key rotation ✅: yeni key provider (priority 200) → yeni `kid`'li token, çalışan uygulama yeniden başlatılmadan kabul etti (tanınmayan kid → JWKS yeniden indirildi); eski key'li token da geçerli. Silinen key'in token'ı JWKS cache süresi boyunca kabul edilmeye devam etti (beklenen; key iptali anlık değil)

### K1 — Keycloak local kurulum ✅ (2026-10-24) — bırakılan Keycloak yolu, A4'te kaldırılır

- `compose.yaml`: `keycloak` servisi (`quay.io/keycloak/keycloak:26.8.1`, `start-dev --import-realm`, host `127.0.0.1:8180`, yönetim portu 9000'de health, bash `/dev/tcp` healthcheck, `depends_on: postgres healthy`)
- Keycloak'ın verisi aynı PostgreSQL sunucusunda ayrı `keycloak` database'i / kullanıcısı: `docker/postgres-init/01-create-keycloak-db.sql` (yalnız boş volume'de çalışır)
- `keycloak/realm-deeptodo.json`: realm `deeptodo` (self-registration açık, email = username, duplicate email yok), client'lar `deeptodo-web` (public, authorization code + PKCE S256) ve `deeptodo-dev` (public, password grant — yalnız local curl), test kullanıcıları alice / bob
- Manual smoke ✅: realm import log'u; discovery (`issuer = http://localhost:8180/realms/deeptodo`); JWKS (RS256 imza key'i + RSA-OAEP şifreleme key'i); password grant ile token; token'sız istek 401, geçerli token 404 (olmayan endpoint, yani kimlik doğrulandı), imzası bozuk token 401 `invalid_token`
- Hata modu ✅: uygulama Keycloak'ı hiç görmeden Keycloak kapanırsa token'lı istek 500; key'ler bir kez alındıktan sonra Keycloak kapanırsa doğrulama devam eder (404)
- Not: access token'da `aud` claim'i yok → K2'de audience mapper + doğrulama

### Refactor — `SecurityProblemHandler` `ProblemDetail` kullanıyor ✅ (2026-10-24)

- Elle kurulan `Map` yerine Spring'in `ProblemDetail` tipi; serialize işi Boot'un `JsonMapper`'ında. Filter zinciri 401 / 403 gövdesi artık MVC tarafındaki ProblemDetail cevaplarıyla aynı şekilde (aynı alanlar, varsayılan `about:blank` type yazılmıyor)
- Manual smoke ✅: token'sız istek → 401 + `WWW-Authenticate` + `application/problem+json`

### (Keycloak geçişi) Kendi authentication kodunu kaldır ✅ (2026-10-24)

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
| V3 | `refresh_tokens` (spec §7.9, halef bağlantısı yok) + `user_id`, `family_id` index'leri — R7–R9 yeniden kurulumunda kullanılır |

## Current schema

`users`, `workspaces`, `refresh_tokens` (V1–V3).

Entity hiyerarşisi: `BaseEntity` (yalnız `id`, UUID v7) → `AuditableEntity` (`created_at` `updatable=false`, `updated_at`; Spring Data auditing). `@Version` base class'ta değil; ihtiyaç duyan entity (Todo, WorkspaceMember) kendisi tanımlar.

## Package layout

```text
common/config     JpaAuditingConfig, ClockConfig
common/domain     BaseEntity, AuditableEntity
common/error      GlobalExceptionHandler, ResourceNotFoundException, ForbiddenOperationException, BusinessRuleViolationException
user/domain       User (email, displayName, status; normalizeEmail), UserStatus, UserRepository, UserExampleListener (örnek)
```

## Known technical debt

| ID | Debt | Planned phase |
|---|---|---|
| TD-3 | `UserExampleListener` ve `UserRepository`'deki deneme sorguları örnek amaçlı; temizlenecek | Phase 3 |
| TD-12 | Validation 400 cevabı hangi alanın hatalı olduğunu söylemiyor (`detail: Invalid request content.`); beklenmeyen 500'ler hâlâ Boot'un `/error` JSON'u (ProblemDetail değil) | Phase 4 (E2 ile) |

## Current learning focus

- Spring Security 7 içeriden: filter chain, `AuthenticationManager` / `DaoAuthenticationProvider`, `SecurityContext`
- JWT (RS256, `kid`, `iss` / `aud` / `exp`) ve key rotation
- Refresh token rotation, reuse detection, transaction sınırları ve concurrency

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
- authentication written in-app with Spring Security (Keycloak tried and dropped 2026-10-25)
- Redis / Kafka only for spec §2.1 learning modules; no microservices
- final v1 includes Docker + public HTTPS deployment
