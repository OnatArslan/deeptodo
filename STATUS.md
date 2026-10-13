# DeepToDo — Status

> `PROJECT_SPEC.md` target architecture/scope'u gösterir.
> Bu dosya repository'nin şu anki gerçek durumunu gösterir.
> Coherent implementation task tamamlandığında güncellenir.

## Last updated

2026-10-13

## Current phase

**Phase 3 (öne alındı) — Authentication**

Karar (2026-10-10): Phase 3'ün authentication kısmı Phase 1'den önce yapılıyor (bkz. spec §18 sıra kararı). DT-002'nin `users` kısmı bu akışa dahil edildi; `workspaces` entity'si Phase 1/2'de.

## Active task

### DT-003 — Register + login + JWT access token üretimi

Status: `IN PROGRESS` (yeniden başlatıldı, 2026-10-11)

Done:
- `spring-boot-starter-security`, `spring-boot-starter-security-oauth2-resource-server`
- `SecurityConfig`: stateless, csrf kapalı, `/api/auth/**` permitAll, geçici `httpBasic`; `PasswordEncoderFactories` (default bcrypt); `AuthenticationManager` = `ProviderManager(DaoAuthenticationProvider)`
- `AppUserDetailsService` / `AppUserDetails` (aktiflik `status == ACTIVE`'den türetilir; ayrı `enabled` kolonu yok)
- JWT üretimi: `JwtConfig` (RSA key'ler `secrets/`'tan, `NimbusJwtEncoder`, `kid`), `JwtProperties`, `TokenService` (`iss`, `sub`=userId, `iat`, `exp`, `jti`, `authorities`), TTL 15m
- `POST /api/auth/register` 201, `POST /api/auth/login` → `TokenResponse`, `GET /api/users/me` (test)
- `AuthExceptionHandler`: `AuthenticationException` → 401, `EmailAlreadyExistException` → 409

Manual smoke ✅ (2026-10-12): register 201; duplicate 409; login 200 + RS256 JWT; yanlış şifre / bilinmeyen kullanıcı / DEACTIVATED → aynı 401; bilinen ve bilinmeyen email için yanıt süresi eşit (~86 ms, timing koruması çalışıyor); GET register 405; `/api/users/me` httpBasic 200, kimliksiz 401; validation 400.

Next: token doğrulama — `oauth2ResourceServer(jwt)` + `JwtDecoder`, `httpBasic` kaldırılacak.

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

## Current schema

`users`, `workspaces` (V1 + V2).

Entity hiyerarşisi: `BaseEntity` (yalnız `id`, UUID v7) → `AuditableEntity` (`created_at` `updatable=false`, `updated_at`; Spring Data auditing). `@Version` base class'ta değil; ihtiyaç duyan entity (Todo, WorkspaceMember) kendisi tanımlar.

## Package layout

```text
common/config     JpaAuditingConfig (DateTimeProvider → Instant)
common/domain     BaseEntity, AuditableEntity
auth/             AuthService, AppUserDetailsService, AppUserDetails, SecurityConfig
auth/jwt          JwtConfig, JwtProperties, TokenService
auth/exception    EmailAlreadyExistException
auth/web          AuthController, AuthExceptionHandler, dto/ (Login/Register Request, RegisterResponse, TokenResponse)
user/domain       User (normalizeEmail), UserStatus, UserRepository, UserExampleListener (örnek)
user/web          UserController (GET /api/users/me, test)
```

Planned: `auth/` — `JwtDecoder` + resource server config, refresh token (`auth/domain/RefreshToken*`).

## Known technical debt

| ID | Debt | Planned phase |
|---|---|---|
| TD-3 | `UserExampleListener` ve `UserRepository`'deki deneme sorguları örnek amaçlı; temizlenecek | Phase 3 |
| TD-4 | Login rate limit (spec §10) henüz yok | Phase 3 |

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
