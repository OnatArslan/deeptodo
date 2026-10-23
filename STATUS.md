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
- Refresh token (2026-10-15, refactor aynı gün):
  - V3 `refresh_tokens` (spec §7.9; `replaced_by_token_id` yok — katı reuse için `used_at` + `family_id` yeterli; push'lanmış V3 Onat kararıyla yerinde düzenlendi + DB reset)
  - `auth/refresh`: `RefreshToken` (scalar `userId`; `isUsed` / `isRevoked` / `isExpired`), `RefreshTokenRepository` (koşullu `markUsed`, `revokeFamily`), `RefreshTokenService` (`issue` / `rotate` / `revokeFamilyOf`), `RotationResult` (sealed: `Rotated` | `Rejected(reason)`)
  - `auth/jwt`: `AccessTokenService` yalnız JWT üretir; `Clock` `common/config/ClockConfig`'te; properties `@ConfigurationPropertiesScan` ile
  - Rotation sırası: bul → revoke/used(reuse→aile revoke)/expired → kullanıcı ACTIVE mi (değilse aile revoke) → koşullu `markUsed` (0 → reuse) → aynı aileden yeni token. Reddetme sonuç olarak döner, revoke normal commit edilir; exception `AuthService`'te transaction dışında (`noRollbackFor` yok)
  - Opaque 256-bit token, DB'de yalnız SHA-256 hex; TTL 14 gün
  - Cookie: `refresh_token`, HttpOnly, SameSite=Strict, Path=/api/auth, Secure `REFRESH_COOKIE_SECURE` ile (local false)
  - `POST /api/auth/register` 201 → id + access token + refresh cookie (karar: register otomatik login)
  - `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout` (204, idempotent)
  - Karar: **katı reuse detection**, grace window yok (spec §10); CSRF kapalı, cookie'li endpoint'ler SameSite=Strict + Path ile korunuyor

Manual smoke ✅ (2026-10-15, refactor sonrası tekrar): register 201 + cookie; login 200; refresh 200 ve cookie döner; 3 ardışık refresh zinciri 200; eski token tekrar → 401 + aile revoke + cookie silinir; revoke sonrası halef → 401; cookie'siz refresh 401; logout 204 sonrası refresh 401; aynı token'la 3 eşzamanlı refresh → 1×200, 2×401, aile revoke (katı reuse'un beklenen sonucu); DEACTIVATED → 401 (token tüketilmeden aile revoke).

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

## Local environment

- PostgreSQL (compose) host'ta `127.0.0.1:5433` → container 5432. Host 5432 bu makinede başka bir projenin (`library-postgres`) Postgres'inde; A1 (2026-10-23) ile taşındı.
- `DB_URL` varsayılanı `jdbc:postgresql://localhost:5433/deeptodo`.

## Applied migrations

| Migration | Purpose |
|---|---|
| V1 | `users` (incl. `password_hash varchar(255)`), `workspaces`; named PKs, no time defaults, `~ '\S'` blank checks |
| V2 | `users.version` kolonu kaldırıldı (spec §21; `@Version` yalnız todos / workspace_members'ta) |
| V3 | `refresh_tokens` (spec §7.9, halef bağlantısı yok) + `user_id`, `family_id` index'leri |

## Current schema

`users`, `workspaces`, `refresh_tokens` (V1–V3).

Entity hiyerarşisi: `BaseEntity` (yalnız `id`, UUID v7) → `AuditableEntity` (`created_at` `updatable=false`, `updated_at`; Spring Data auditing). `@Version` base class'ta değil; ihtiyaç duyan entity (Todo, WorkspaceMember) kendisi tanımlar.

## Package layout

```text
common/config     JpaAuditingConfig, ClockConfig
common/domain     BaseEntity, AuditableEntity
auth/             AuthService (register/login/refresh/logout), AuthTokens, AppUserDetailsService, AppUserDetails, SecurityConfig
auth/jwt          JwtConfig, JwtProperties, AccessTokenService
auth/refresh      RefreshToken, RefreshTokenRepository, RefreshTokenProperties, RefreshTokenService, RotationResult
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
