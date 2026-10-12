# DeepToDo — Status

> `PROJECT_SPEC.md` target architecture/scope'u gösterir.
> Bu dosya repository'nin şu anki gerçek durumunu gösterir.
> Coherent implementation task tamamlandığında güncellenir.

## Last updated

2026-10-10

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
| V1 (local, not committed) | `users` (incl. `password_hash varchar(255)`, `version`), `workspaces`; named PKs, no time defaults, `~ '\S'` blank checks |

## Current schema

`users`, `workspaces` per local V1. `users.version` remains only because `BaseEntity` maps `@Version` (TD-6).

## Package layout

```text
common/config     JpaAuditingConfig (DateTimeProvider → Instant)
common/domain     BaseEntity, AuditableEntity
user/domain       User (normalizeEmail), UserStatus, UserRepository, UserExampleListener (örnek)
```

Planned (spec §5): `auth/` — AuthService, UserDetailsService, `config/SecurityConfig`, JWT, refresh token.

## Known technical debt

| ID | Debt | Planned phase |
|---|---|---|
| TD-2 | `users.version` spec'te yok (§21); TD-6 ile birlikte çözülecek | Before Todo entity |
| TD-3 | `UserExampleListener` ve `UserRepository`'deki deneme sorguları örnek amaçlı; temizlenecek | Phase 3 |
| TD-4 | Login rate limit (spec §10) henüz yok | Phase 3 |
| TD-6 | `BaseEntity` her entity'ye `@Version` ekliyor (spec §21: mekanik `@Version` yok) | Before Todo entity |

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
