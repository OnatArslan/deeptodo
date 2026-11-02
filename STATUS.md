# DeepToDo — Status

> `PROJECT_SPEC.md` target architecture/scope'u gösterir.
> Bu dosya repository'nin şu anki gerçek durumunu gösterir.
> Coherent implementation task tamamlandığında güncellenir.

## Last updated

2026-11-02

## Current phase

**Yeniden kurulum — S (Spring temelleri) → R (Authentication) → K (Keycloak)**

Karar (2026-11-02): projenin asıl amacı Spring Boot, Spring Data JPA ve Spring Security'yi öğrenmek. Proje bootstrap'a geri
alındı (ROADMAP A6); her adım bir ders olarak, yazılan kodun tamamı chat'te anlatılarak yeniden kurulur (`CLAUDE.md` →
"Anlatım: her adım bir ders").

## Active task

_None._ Sıradaki: **S2** — PostgreSQL + Flyway: DataSource / HikariCP auto-config'i, `V1__create_users.sql`.

## Phase status

| Phase | Status |
|---|---|
| Phase 0 — Bootstrap | ✅ (A6 ile yeniden bu noktada) |
| S — Spring temelleri | 🔄 S1 ✅ |
| Phase 3 — Security (R1–R9, sonra K) | ⬜ yeniden kurulacak |
| Phase 1 — Todo Core | ⬜ |
| Phase 2 — Workspace / Collaboration / Querying | ⬜ |
| Phase 4 — Testing + Production + Deployment | ⬜ |

## Completed tasks

### S1 — Boot iskeleti ✅ (2026-11-02)

- `application.yaml`: actuator web exposure açıkça `health` (varsayılanla aynı; production'da yalnız health)
- `application-local.yaml`: `local` profilinde `health, beans, conditions, configprops, env, mappings` + health detayları
- `pom.xml`: `spring-boot-maven-plugin` → `./mvnw spring-boot:run` `local` profilini açar; jar'a etkisi yok
- `DeeptodoApplication`: paketin component scan kökü olduğunu söyleyen yorum
- Gözlem ✅: `--debug` condition raporu (141 positive / 86 negative / 11 unconditional); Boot 4'te auto-configuration'lar
  teknoloji modüllerinde (`spring-boot-tomcat` 5, `spring-boot-webmvc` 6, çekirdek `spring-boot-autoconfigure` 12); JDBC
  modülü olmadığı için `DataSourceAutoConfiguration` raporda hiç yok; starter jar'ı kod içermez (yalnız manifest / lisans);
  264 bean; `/actuator/env` aynı key için iki kaynağı sırasıyla gösteriyor (local profili kazanır, değerler `******`)
- Doğrulama ✅: `./mvnw verify` geçti; `spring-boot:run` → `active: "local"`, 0.74 sn; jar profilsiz → `/actuator/beans` 404,
  health 200; jar + `SPRING_PROFILES_ACTIVE=local` → 200; + `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health` → 404 (env,
  profil dosyasını ezer). Geçici demo (silindi): scan kökünün altındaki controller 200, `com.onatarslan.other`'daki 404;
  kendi `JsonMapper` bean'imiz → `JacksonAutoConfiguration#jacksonJsonMapper` "found beans … myJsonMapper" ile geri çekildi

### A6 — Bootstrap'a dönüş ✅ (2026-11-02)

- Önce R8a commit edildi (`2fb732e`) ve son hal `inapp-auth-r8a` tag'i ile işaretlendi (push edildi)
- Kaldırıldı: `auth`, `user`, `common` paketlerinin tamamı; V1–V3 migration'ları; `@ConfigurationPropertiesScan`;
  dependency'ler: PostgreSQL driver, Flyway (+ `flyway-database-postgresql`), Data JPA, Security, BouncyCastle,
  OAuth2 resource server; `application.yaml`'daki datasource, JPA, MVC ProblemDetail ve `deeptodo.security.*` ayarları
- Kaldı: `DeeptodoApplication`, `contextLoads` testi, `compose.yaml` (PostgreSQL), pom'da webmvc, validation, actuator (+ test starter'ları);
  `secrets/` (gitignore, R4'te yeniden kullanılır)
- Local DB: `docker compose down -v` ile volume silindi, boş DB ile yeniden açıldı (Onat'ın onayıyla; migration'lar hiçbir
  ortama deploy edilmemişti, yalnız local test verisi gitti). `library-postgres`'e dokunulmadı
- Doğrulama ✅: `./mvnw clean verify` geçti (`contextLoads`); uygulama 0.83 sn'de açıldı, health `UP`; `/api/users/me` 404;
  DB'de public şemada 0 tablo

### Önceki kurulum (referans, `inapp-auth-r8a` tag'i)

A1–A5, R1–R8a: hata sözleşmesi, filter chain, register (Argon2id), login (`DaoAuthenticationProvider`), RS256 access token,
resource server, refresh token ve rotation. Her adımın ayrıntılı doğrulama kaydı o tag'deki `STATUS.md`'de ve commit
mesajlarında. Yeni adımlarda ROADMAP "ilk:" ile o kurulumun commit'ini gösterir.

## Local environment

- PostgreSQL (compose) host'ta `127.0.0.1:5433` → container 5432. Host 5432 bu makinede başka bir projenin (`library-postgres`) Postgres'inde; A1 (2026-10-23) ile taşındı.
- Uygulama şu an DB'ye bağlanmıyor (S2'de bağlanacak).
- `./mvnw spring-boot:run` `local` profiliyle açılır (pom); IntelliJ run configuration'ında Active profiles: `local`.

## Applied migrations

_Yok_ (S2'de V1).

## Current schema

_Boş._

## Package layout

```text
com.onatarslan.deeptodo   DeeptodoApplication
```

## Known technical debt

| ID | Debt | Planned phase |
|---|---|---|
| TD-12 | (S5'ten sonra geçerli olacak) Validation 400 cevabı hangi alanın hatalı olduğunu söylemiyor; beklenmeyen 500'ler Boot'un `/error` JSON'u | Phase 4 (E2 ile) |

## Current learning focus

- Spring Boot çekirdeği: auto-configuration, bean'ler, configuration properties
- Spring Data JPA: persistence context, entity yaşam döngüsü, transaction
- Spring Security 7 içeriden: filter chain, `AuthenticationManager`, `SecurityContext`, JWT, refresh token

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
- authentication written in-app with Spring Security (Keycloak tried and dropped 2026-10-25) — **2026-10-31: R1–R9 bitince Keycloak'a geçilir (ROADMAP R bölümündeki karar)**
- Redis / Kafka only for spec §2.1 learning modules; no microservices
- final v1 includes Docker + public HTTPS deployment
