# DeepToDo — Roadmap

Hedef: [SPEC.md](SPEC.md)'deki küçük projeyi **Onat'ın kendi yazmasıyla** v1'e getirmek
(bitti tanımı: SPEC §8). Her adımın kuralları spec'te; hangi bölümün hangi adıma ait olduğu SPEC'in başındaki tabloda.
Claude her adımı bir görev kartıyla tanımlar, Onat yazar ve commit eder, Claude review eder (`CLAUDE.md` → "Çalışma biçimi").

**Sıra (karar 2026-11-13): Spring Core → Web → Data JPA → Security**, Onat'ın kurslarıyla aynı sırada. Bedeli bilinçli:
- J1'e kadar veri bellekte durur.
- S6'ya kadar kayıtların sahibi yoktur; sahiplik sonradan dolu tablolara eklenir. Bu da gerçek projelerde sık yaşanan bir iştir ve kendi başına öğretir.

Bir adım tek bir şey öğretir ve ~300 satırı geçmez. Adım review'dan geçince `[x]` + commit SHA'sı yazılır. Sıra değişikliği Onat'ın kararıdır.

İşaretler: `[ ]` yapılmadı · `[x]` tamamlandı · 🔬 derin anlatım (auth)

Geçmiş: 2026-11-11'e kadarki workspace tabanlı büyük sürüm git geçmişinde. Kendi auth implementasyonunun son hali
`inapp-auth-r8a` tag'inde; Claude'un ders olarak yazdığı Boot iskeleti `529a964`'te. İkisi de yalnız referans.

---

## C — Spring Core & Boot

- [ ] **C1** Kurulum: start.spring.io (Boot 4.1, Java 21, Maven; **yalnız** webmvc, validation, actuator — DB yok);
  `@SpringBootApplication`, auto-configuration ve condition raporu, `application.yaml`, `local` profili, `/actuator/health` UP
- [ ] **C2** Domain ve bean'ler: `Todo` domain sınıfı (durum makinesi, `completedAt` kuralı), `TodoRepository` arayüzü + bellekte
  implementasyonu, `TodoService`, `Clock` bean'i, `@ConfigurationProperties` (liste sınırları); durum makinesi için unit test

## W — Spring Web

- [ ] **W1** Todo REST: `POST`, `GET /{id}`, `GET` (liste, şimdilik sayfasız), `DELETE`; record DTO'lar, Bean Validation,
  `ResponseEntity` + `Location`
- [ ] **W2** Hata sözleşmesi: `@RestControllerAdvice`, `ProblemDetail`, HTTP bilmeyen domain exception'ları (404 / 409),
  validation 400 + `errors` listesi, bozuk JSON 400, 405
- [ ] **W3** `PATCH`: JSON Merge Patch anlamı ("alan yok" ile `null` farkı), durum geçişleri (geçersizi 409)

## J — Spring Data JPA

- [ ] **J1** PostgreSQL + Flyway: driver, `spring-boot-starter-flyway` + `flyway-database-postgresql`, local DB volume'ünün
  sıfırlanması, `todos` migration'ı (sahipsiz), `Todo`'nun entity'ye dönüşmesi (UUID v7, enum string, auditing),
  bellekteki repository yerine Spring Data JPA; `ddl-auto: validate`, `open-in-view: false`
- [ ] **J2** Liste: sayfalama + sıralama (kendi `Page` DTO'su); `@Version` + PATCH'te version (çakışma 409)
- [ ] **J3** Projects: migration, CRUD + archive, harf duyarsız tekil ad; todo → project (`@ManyToOne`)
- [ ] **J4** Tags + `TodoTag` join entity: tag CRUD, todo'ya ekle / çıkar (idempotent), silmede temizlik
- [ ] **J5** Filtreleme (`Specification`) + N+1 ölçümü ve çözümü (fetch join / `@EntityGraph`), index (`EXPLAIN ANALYZE`)

## S — Spring Security 🔬

- [ ] **S1** `SecurityFilterChain`: stateless, CSRF kararı, health açık, gerisi kilitli; 401 / 403 `ProblemDetail`
- [ ] **S2** Register: `users` migration'ı + `User` entity, `PasswordEncoder` (Argon2id, delegating), `POST /api/auth/register`,
  email normalize, tekil email 409
- [ ] **S3** Login: `AuthenticationManager` → `DaoAuthenticationProvider` → `UserDetailsService`; üç başarısızlık aynı 401
- [ ] **S4** Access token: RSA key'ler (`secrets/`), `JwtEncoder`, `kid`, claim'ler; login token döner
- [ ] **S5** Resource server: `JwtDecoder` (RS256, `iss` / `aud` / `exp`), `CurrentUser`, `GET /api/users/me`
- [ ] **S6** Sahiplik: todos / projects / tags / todo_tags'e sahip kolonu (dolu tablolara), aynı sahip composite FK'leri,
  bütün sorgular `id + ownerId`, başkasının kaydı 404 (büyürse ikiye bölünür)
- [ ] **S7** Refresh token: `refresh_tokens` migration'ı, üretim + cookie, `POST /api/auth/refresh` (koşullu UPDATE ile rotation)
- [ ] **S8** Reuse detection (aile revoke) + `POST /api/auth/logout` + `DELETE /api/users/me` (kapatma, bütün token'ların revoke'u)

## Q — Test ve paket

- [ ] **Q1** Testcontainers altyapısı + SPEC §8'deki entegrasyon testleri (auth akışı, sahiplik, eşzamanlı refresh, constraint'ler, optimistic locking)
- [ ] **Q2** OpenAPI (springdoc) + Bearer security scheme
- [ ] **Q3** Dockerfile (multi-stage, non-root) + compose'a app servisi + README (çalıştırma, tasarım kararları) → v1

## O — Opsiyonel (v1'den sonra)

- [ ] **O1** Keycloak'a geçiş: kendi auth'umuzu kaldırıp resource server'ı Keycloak'a bağlamak; S adımlarıyla karşılaştırma
- [ ] **O2** Redis: login rate limit (429 + `Retry-After`)
- [ ] **O3** Kafka: transactional outbox + bir event (ör. `UserDeactivated`)
- [ ] **O4** CI (GitHub Actions) + public HTTPS deploy
