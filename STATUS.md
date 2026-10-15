# DeepToDo — Status

> `PROJECT_SPEC.md` target architecture/scope'u gösterir.
> Bu dosya repository'nin şu anki gerçek durumunu gösterir.
> Coherent implementation task tamamlandığında güncellenir.

## Last updated

2026-10-15

## Current phase

**Phase 3 (öne alındı) — Authentication**

Karar (2026-10-10): Phase 3'ün authentication kısmı Phase 1'den önce yapılıyor (bkz. spec §18 sıra kararı). DT-002'nin `users` kısmı bu akışa dahil edildi; `workspaces` entity'si Phase 1/2'de.

## Active task

### DT-003 — Register + login + JWT + refresh token

Status: `IN PROGRESS`

Done:
- Access token: RS256 JWT (`TokenService`), `NimbusJwtDecoder` + timestamp/issuer validator, `oauth2ResourceServer(jwt)`, `authorities` claim → authorities (prefix yok); `BearerTokenResolver` `/api/auth/**`'da token okumaz; `/actuator/health` permitAll
- Refresh token (2026-10-15):
  - V3 `refresh_tokens` (spec §7.9: `used_at`, `replaced_by_token_id` self-FK, `expires_at > created_at`, cascade yok); `RefreshToken` entity (scalar `userId`), `RefreshTokenRepository` (koşullu `markUsed`, `linkSuccessor`, `revokeFamily`)
  - Opaque 256-bit token, DB'de yalnız SHA-256 hex; TTL 14 gün
  - Cookie: `refresh_token`, HttpOnly, SameSite=Strict, Path=/api/auth, Secure `REFRESH_COOKIE_SECURE` ile (local false)
  - `POST /api/auth/register` 201 → id + access token + refresh cookie (karar: register otomatik login)
  - `POST /api/auth/login` → access token + refresh cookie (yeni aile)
  - `POST /api/auth/refresh` → rotation; kullanılmış token tekrar gelirse aile revoke (karar: **katı reuse detection**, grace window yok); DEACTIVATED kullanıcı → aile revoke + 401; reuse revoke'u `noRollbackFor` ile commit edilir
  - `POST /api/auth/logout` → aile revoke, cookie temizlenir, 204 (idempotent)
  - CSRF kapalı kalıyor; cookie'li endpoint'ler SameSite=Strict + Path ile korunuyor (spec §10 kararı)

Manual smoke ✅ (2026-10-15): register 201 + cookie; login 200; refresh 200 ve cookie döner, zincir `replaced_by` ile bağlı; eski token tekrar → 401 + aile revoke + cookie silinir; revoke sonrası halef → 401; cookie'siz refresh 401; logout 204 sonrası refresh 401; aynı token'la 3 eşzamanlı refresh → 1×200, 2×401, aile revoke (katı reuse'un beklenen sonucu); DEACTIVATED → 401; token_hash hep 64 karakter.

Next: hesap kapatma (`DELETE /api/users/me` + kullanıcının bütün aileleri revoke), login rate limit, `CurrentUser` yardımcısı.

## Phase status

| Phase | Status |
|---|---|
| Phase 0 — Bootstrap | ✅ Done |
| Phase 1 — Todo Core | ⏸ Paused (users schema done, workspace entity pending) |
| Phase 2 — Workspace / Collaboration / Querying | ⬜ |
| Phase 3 — Security | 🔄 In progress (authentication first) |
| Phase 4 — Testing + Production + Deployment | ⬜ |

## Completed tasks

### DT-001 — Bootstrap baseline ✅ (2026-10-01)

- `compose.yaml`: PostgreSQL `17.6`, named volume `postgres-data`, `pg_isready` healthcheck
- `pom.xml`: data-jpa, PostgreSQL driver (runtime), `spring-boot-starter-flyway` + `flyway-database-postgresql`, actuator
- `application.yaml`: env-overridable datasource (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`), `ddl-auto: validate`, `open-in-view: false`
- `src/main/resources/db/migration/` created
- Manual smoke: app starts, Flyway creates `flyway_schema_history`, `/actuator/health` → `UP`

## Applied migrations

| Migration | Purpose |
|---|---|
| V1 | `users` (incl. `password_hash varchar(255)`), `workspaces`; named PKs, no time defaults, `~ '\S'` blank checks |
| V2 | `users.version` kolonu kaldırıldı (spec §21; `@Version` yalnız todos / workspace_members'ta) |
| V3 | `refresh_tokens` (spec §7.9) + `user_id`, `family_id` index'leri |

## Current schema

`users`, `workspaces`, `refresh_tokens` (V1–V3).

Entity hiyerarşisi: `BaseEntity` (yalnız `id`, UUID v7) → `AuditableEntity` (`created_at` `updatable=false`, `updated_at`; Spring Data auditing). `@Version` base class'ta değil; ihtiyaç duyan entity (Todo, WorkspaceMember) kendisi tanımlar.

## Package layout

```text
common/config     JpaAuditingConfig (DateTimeProvider → Instant)
common/domain     BaseEntity, AuditableEntity
auth/             AuthService, AuthTokens, AppUserDetailsService, AppUserDetails, SecurityConfig
auth/domain       RefreshToken, RefreshTokenRepository
auth/jwt          JwtConfig, JwtProperties, RefreshTokenProperties, TokenService (access + refresh)
auth/exception    EmailAlreadyExistException, InvalidRefreshTokenException
auth/web          AuthController, AuthExceptionHandler, RefreshTokenCookies, dto/
user/             UserService
user/domain       User (normalizeEmail), UserStatus, UserRepository, UserExampleListener (örnek)
user/web          UserController (GET /api/users/me), dto/MeResponse
```

## Known technical debt

| ID | Debt | Planned phase |
|---|---|---|
| TD-3 | `UserExampleListener` ve `UserRepository`'deki deneme sorguları örnek amaçlı; temizlenecek | Phase 3 |
| TD-4 | Login rate limit (spec §10) henüz yok; `ProviderManager`'a event publisher bağlı değil | Phase 3 |
| TD-8 | Rotation / reuse için concurrency testi (spec §15 istisnası) yazılmadı — Onat kararıyla ertelendi; Testcontainers gerekecek | Phase 4 başı |
| TD-9 | `REFRESH_COOKIE_SECURE` production'da true verilmeli | Phase 4 deploy |

## Current learning focus

- Spring Security 7: filter chain, `AuthenticationManager` / `DaoAuthenticationProvider` akışı
- Password hashing: `DelegatingPasswordEncoder`, hash upgrade
- Error contract: `ProblemDetail`, authentication hatalarında bilgi sızdırmama

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
- no Redis/Kafka/microservices
- final v1 includes Docker + public HTTPS deployment
