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

---

## Kurs eşleştirmesi (karar 2026-11-15)

Tutor'lar (claude.ai) projeyle paralel gider. Spring kursları **tam zamanında** okunur: bir adıma başlamadan hemen önce o
adımın satırındaki başlıklar okunur, sonra adım yazılır. Görev kartı bu başlıkları "Önce oku" olarak verir ve kursun derin
anlattığını tekrar etmez; kart, kavramın bu projede nasıl uygulanacağına odaklanır.

Bir adımın akışı:

```text
"sıradaki" → görev kartı ("Önce oku" satırıyla) → Onat tutor'da başlıkları okur → adımı yazar
→ "review" → geçti → ROADMAP [x] + STATUS → Onat commit eder → "sıradaki"
```

Spring kursları 2026-11-18'de inceltildi: 65 başlıktan 44'e (Core 9, Web 8, JPA1 7, JPA2 8, Sec 12; Sec'te OAuth2 / OIDC iki başlık: kavramlar ve Keycloak). SQL'in eski schema ve query kursları aynı gün tek bir 16 başlıklık SQL kursuyla değiştirildi. Az kullanılan konular
başlıklardan çıkarıldı ve her dosyanın sonundaki "Ayrıca sorabileceklerin" listesine taşındı. Aşağıdaki numaralar bu yeni
listeye göredir.

Kurs dosyaları chat sırasıyla ilerleyecek şekilde kurulu (örneğin Core'da 1. chat 1–5, 2. chat 6–9). Adımlar ise farklı
chat'lerden başlık ister. Bu yüzden başlık **doğrudan istenir**: spring-tutor'da yeni bir chat'te "Spring Core kursu,
4. başlık". Kurs dosyalarında ilerleme başlık numarasıyla tutulabilir (Java kursundaki gibi "Tamamlanan: …"); bu satırı
Onat claude.ai'deki dosyalara kendisi ekler.

Kısaltmalar: **Core** = Spring Core, **Web** = Spring Web, **JPA1** = JPA 1 (mapping), **JPA2** = JPA 2 (sorgu),
**Sec** = Spring Security, **SQL** = SQL kursu (sql-tutor; 1–12 query, 13–16 tasarım). Numaralar kurs dosyalarındaki başlık numaraları. Bir başlık birden
fazla adıma yarıyorsa yalnız ilk adımda okunur; sonraki adımda "tekrar" diye geçer.

| Adım | Önce oku | Java kursunda bağı |
|---|---|---|
| C1 | Core 1, 4, 5, 6, 9 | |
| C2 | Core 2, 3 | 33 (State), 20 (sealed / pattern matching) |
| W1 | Web 1, 2, 3, 5 | |
| W2 | Web 4 | 5 (exception'lar) |
| W3 | tekrar: Web 2'nin Jackson kısmı | |
| J1 | JPA1 1, 2, 6, 7 · JPA2 1, 7 · Core 7 · SQL 13 | 31–32 (Proxy) |
| J2 | JPA2 4, 8 · SQL 16 (tekrar: JPA1 7'nin `@Version` kısmı) | |
| J3 | JPA1 3, 4 · JPA2 2 · SQL 14 | |
| J4 | JPA1 5 | |
| J5 | JPA2 3, 5, 6 · SQL 15 | |
| S1 | Sec 1, 9 · Web 6 | 36 (Chain of Responsibility) |
| S2 | Sec 3 | 33 (Strategy: `PasswordEncoder`) |
| S3 | Sec 2, 4 | |
| S4 | Sec 5 | |
| S5 | Sec 6, 8 | |
| S6 | tekrar: SQL 14'ün composite FK kısmı ve Sec 8'in resource bazlı yetki kısmı | |
| S7 | Sec 7 | |
| S8 | tekrar: Sec 7 (reuse detection, revoke) ve Sec 9 (CSRF) | |
| Q1 | Sec 12 | |
| Q2 | Web 8 | |
| O1 | Sec 10, 11 | |

Projede karşılığı olmayan başlıklar: Core 8 (event'ler, async, scheduling) ve Web 7 (HTTP client'lar). Bunlar v1'den sonra
ya da mülakat hazırlığında okunur. Java kursu ve SQL kursunun query kısmı (1–12) kendi hızında gider; tablo yalnız doğrudan bağları gösterir.
