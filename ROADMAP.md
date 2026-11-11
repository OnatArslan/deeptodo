# DeepToDo — Roadmap

Hedef: `PROJECT_SPEC.md` v6'daki küçük projeyi **Onat'ın kendi yazmasıyla** v1'e getirmek (spec §11).
Claude her adımı bir görev kartıyla tanımlar, Onat yazar ve commit eder, Claude review eder (`CLAUDE.md` → "Çalışma biçimi").

Bir adım tek bir şey öğretir ve ~300 satırı geçmez. Adım review'dan geçince `[x]` + commit SHA'sı yazılır. Sıra değişikliği Onat'ın kararıdır.

İşaretler: `[ ]` yapılmadı · `[x]` tamamlandı · 🔬 derin anlatım (auth)

Geçmiş: 2026-11-11'e kadarki workspace tabanlı büyük sürüm git geçmişinde. Kendi auth implementasyonunun son hali
`inapp-auth-r8a` tag'inde; Claude'un ders olarak yazdığı Boot iskeleti `529a964`'te. İkisi de yalnız referans.

---

## P — Proje temeli

- [ ] **P1** Kurulum: start.spring.io (Boot 4.1, Java 21, Maven; webmvc, validation, actuator, data-jpa, flyway, postgresql) +
  `flyway-database-postgresql`; `compose.yaml` ile DB, datasource env'den (`${DB_URL:…}`), `ddl-auto: validate`,
  `open-in-view: false`; uygulama açılır, `/actuator/health` UP (`db` dahil)
- [ ] **P2** `users` migration'ı (V1, `password_hash` hariç) + `User` entity (UUID v7, enum string, `Instant`) + `UserStatus` + `UserRepository`;
  `created_at` / `updated_at` için JPA auditing + `Clock` bean'i
- [ ] **P3** Hata sözleşmesi: `@RestControllerAdvice`, HTTP bilmeyen domain exception'ları (404 / 409), validation ve bozuk JSON 400,
  hepsi `ProblemDetail`

## A — Authentication 🔬

- [ ] **A1** Spring Security: `SecurityFilterChain` (stateless, CSRF kararı, health açık, gerisi kilitli), 401 / 403 `ProblemDetail`
- [ ] **A2** Register: `password_hash` migration'ı, `PasswordEncoder` (Argon2id, delegating), `POST /api/auth/register`, email normalize, tekil email 409
- [ ] **A3** Login: `AuthenticationManager` → `DaoAuthenticationProvider` → `UserDetailsService`; üç başarısızlık aynı 401
- [ ] **A4** Access token: RSA key'ler (`secrets/`), `JwtEncoder`, `kid`, claim'ler; login token döner
- [ ] **A5** Resource server: `JwtDecoder` (RS256, `iss` / `aud` / `exp`), `CurrentUser`, `GET /api/users/me`
- [ ] **A6** Refresh token: `refresh_tokens` migration'ı, üretim + cookie, `POST /api/auth/refresh` (koşullu UPDATE ile rotation)
- [ ] **A7** Reuse detection (aile revoke) + `POST /api/auth/logout` + `DELETE /api/users/me` (kapatma, bütün token'ların revoke'u)

## T — Todo

- [ ] **T1** `todos` migration'ı + `Todo` entity (status / priority, `completed_at` CHECK, `@Version`) + `POST` / `GET /api/todos/{id}` (sahiplik sorguda)
- [ ] **T2** Liste (sayfalama + sıralama), `PATCH` (status geçişleri, version çakışması 409), `DELETE`
- [ ] **T3** Projects: migration, CRUD + archive; todo → project (`@ManyToOne`, aynı sahip composite FK)
- [ ] **T4** Tags + `TodoTag` join entity: tag CRUD, todo'ya ekle / çıkar, silmede temizlik
- [ ] **T5** Filtreleme (`Specification`) + N+1 ölçümü ve çözümü (fetch join / `@EntityGraph`), gerekirse index (`EXPLAIN ANALYZE`)

## Q — Test ve paket

- [ ] **Q1** Testcontainers altyapısı + spec §10'daki entegrasyon testleri (auth akışı, sahiplik, eşzamanlı refresh, constraint'ler, optimistic locking)
- [ ] **Q2** OpenAPI (springdoc) + Bearer security scheme
- [ ] **Q3** Dockerfile (multi-stage, non-root) + compose'a app servisi + README (çalıştırma, tasarım kararları) → v1

## O — Opsiyonel (v1'den sonra)

- [ ] **O1** Keycloak'a geçiş: kendi auth'umuzu kaldırıp resource server'ı Keycloak'a bağlamak; A adımlarıyla karşılaştırma
- [ ] **O2** Redis: login rate limit (429 + `Retry-After`)
- [ ] **O3** Kafka: transactional outbox + bir event (ör. `UserDeactivated`)
- [ ] **O4** CI (GitHub Actions) + public HTTPS deploy
