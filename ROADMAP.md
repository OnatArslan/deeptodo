# DeepToDo — Roadmap

Hedef: projeyi bugünkü noktadan **public HTTPS'te çalışan v1**'e getirmek (spec §17), sonra Redis / Kafka öğrenme modülleri (spec §2.1).
Projenin asıl amacı Spring Boot, Spring Data JPA ve Spring Security'yi öğrenmek. **2026-11-02'de proje bootstrap'a geri alındı**
(A6): her şey S ve R adımlarında, yazılan kodun tamamı chat'te ders gibi anlatılarak yeniden kurulur. Önceki kurulumun son hali
`inapp-auth-r8a` tag'inde (yalnız referans; adımlarda "ilk:" ile o kurulumun commit'i verilir).

Kurallar (`CLAUDE.md`): her prompt bir adım, her adım `develop`'a tek commit (~300 satır / 10 dosya). Claude adımı yazar ve anlatır;
Onat sağ panelde okur, bir sonraki "devam"da commit + push edilir.
Her adım bir ders (`CLAUDE.md` → "Anlatım: her adım bir ders"); 🔬 işaretli adımlarda wire seviyesi ve hata modları da gösterilir.
Adım bitince `[x]` yazılır; commit'ten sonra kısa SHA eklenir (2026-10-25'e kadarki adımlarda PR numarası). Sıra değişikliği Onat'ın kararıdır.

İşaretler: `[ ]` yapılmadı · `[x]` tamamlandı · ⚠️ Onat'ın onayı gerekir · 🔬 derin anlatım

---

## A — Temel

- [x] **A1** Ortam: deeptodo-postgres'in host portunu çakışmayan bir porta taşı (5432'de başka proje var) (#2)
- [x] **A2** Hata sözleşmesi: `common/error` (404 / 403 / 409) + filter zinciri 401 / 403 ProblemDetail + validation ProblemDetail (#3, #6)
- [x] **A3** Auth sıfırlama planı: ROADMAP, spec v5, CLAUDE.md (#9)
- [x] **A4** (#10) Auth sıfırlama: Keycloak (compose, realm, database), `auth` paketinin tamamı, security dependency'leri ve `/api/users/me` kaldırılır.
  DB şeması (`users.password_hash`, `refresh_tokens`) olduğu gibi kalır; yeniden kurulumda kullanılır

- [x] **A5** (7e9d0fd) Çalışma akışı: PR yok, `develop`'a doğrudan commit; adım Onat okuyana kadar commit edilmeden bekler
- [x] **A6** Bootstrap'a dönüş: auth / user / common kodu, V1–V3 migration'ları ve JPA / Flyway / Security dependency'leri kaldırılır,
  local DB volume'ü sıfırlanır (Onat'ın onayıyla; migration'lar hiçbir ortama deploy edilmemişti). Önceki hal `inapp-auth-r8a` tag'inde.
  Anlatım biçimi: her adım bir ders, yazılan kodun tamamı chat'te

Geçmiş (bırakılan yol, kayıt için): Keycloak'a geçiş planı (#4), kendi auth kodunun kaldırılması (#5), Keycloak local kurulum (#7),
Keycloak resource server sıkılaştırma (#8). Kod git geçmişinde; kavramlar (`kid`, `iss` / `aud`, key rotation) R adımlarında kullanılır.
Commit edilmemiş eski user / workspace kodu `wip/user-workspace-reference` branch'inde referans olarak duruyor.

## S — Spring temelleri (yeniden kurulum, 2026-11-02)

- [ ] **S1** Boot iskeleti: `@SpringBootApplication`'ın üç parçası, starter'lar, auto-configuration ve condition raporu (`--debug`),
  embedded Tomcat, actuator health, `application.yaml` ve property kaynakları (env ile ezme)
- [ ] **S2** PostgreSQL + Flyway: driver + `spring-boot-starter-flyway`, DataSource / HikariCP auto-config'i, `V1__create_users.sql`
  (isimli constraint'ler, CHECK'ler), `flyway_schema_history` ve checksum; uygulanmış migration neden değişmez
- [ ] **S3** JPA entity: `spring-boot-starter-data-jpa`, `BaseEntity` (UUID v7), `User` + `UserStatus`, `UserRepository`;
  `ddl-auto: validate`, `open-in-view: false`; persistence context, entity yaşam döngüsü, dirty checking, derived query
- [ ] **S4** Auditing ve zaman: `AuditableEntity`, JPA auditing + `Clock` bean'i, `Instant` / `timestamptz`
- [ ] **S5** Hata sözleşmesi: `ProblemDetail`, `@RestControllerAdvice`, HTTP bilmeyen domain exception'ları (404 / 403 / 409),
  validation ve 405 cevaplarının ProblemDetail'i

## R — Authentication (spec §10, Phase 3) 🔬

**Karar (2026-10-31):** auth'u öğrenmek için R1–R9 (refresh token dahil) burada yazılır; ardından kendi auth'umuz kaldırılır ve
**Keycloak**'a geçilir (K serisi). Gerekçe: Onat gerçek projelerde kendi auth'unu yazmayacak, Keycloak tarzı bir kimlik sağlayıcı
kullanacak. Kendi auth'umuzun son hali bir git tag'i ile işaretlenir; K adımlarının anlatımında "burada biz kendimiz şöyle
yapmıştık" karşılaştırması yapılır. R10 / R11 Keycloak'ta karşılanır.

- [ ] **R1** Spring Security: dependency'nin tek başına yaptığı (her şey kilitli, üretilmiş parola), sonra kendi `SecurityFilterChain`'imiz:
  stateless, CSRF kararı, health açık, 401 / 403 ProblemDetail; filter zincirini TRACE log ile gözlemleme _(ilk: 82087a7)_
- [ ] **R2** Register: `PasswordEncoder` (Argon2id + bcrypt doğrulama), `UserService.create` (`@Transactional`), `POST /api/auth/register`,
  DTO validation, email normalize, tekil email → 409 (yarışta DB UNIQUE) _(ilk: 2961fd0, 9e02bc7)_
- [ ] **R3** Login: `AuthenticationManager` → `DaoAuthenticationProvider` → `UserDetailsService`; timing attack koruması; aynı 401;
  eski hash'in yükseltilmesi (`UserDetailsPasswordService`) _(ilk: 72afcec)_
- [ ] **R4** Access token: RSA key'lerin properties'e bağlanması, `RSAKey` + `kid` (thumbprint), `JwtEncoder`, claim'ler
  (`iss`, `sub`, `aud`, `iat`, `exp`, `jti`); login token döner _(ilk: 06b419e, 887cd93)_
- [ ] **R5** Resource server: `JwtDecoder` (yalnız RS256, `iss` / `aud` / `exp`), `CurrentUser` + converter (DB'siz), `GET /api/users/me` _(ilk: 683bee4)_
- [ ] **R6** Refresh token: migration `refresh_tokens`, opaque 256-bit, DB'de SHA-256 hash, HttpOnly + SameSite=Strict + Path cookie _(ilk: 2e07e09)_
- [ ] **R7** Rotation: `POST /api/auth/refresh`, koşullu `markUsed`, aynı ailede halef, kullanıcı ACTIVE kontrolü, auth uçlarında Bearer header'ı yok sayılır _(ilk: 2fb732e)_
- [ ] **R8** Testcontainers altyapısı + eşzamanlı refresh testi (spec §15 istisnası)
- [ ] **R9** Reuse detection: kullanılmış token tekrar gelirse ailenin revoke'u; reddetmeyi sonuç nesnesiyle dönmek ve transaction sınırı
- [ ] ~~**R10**~~ Logout + hesap: _(Keycloak'a geçişte K serisinde)_ `POST /api/auth/logout`, `PATCH / DELETE /api/users/me`; hesap kapatınca bütün refresh token'ların revoke'u
- [ ] ~~**R11**~~ Login rate limit: _(Keycloak brute-force koruması)_ in-memory, email + IP, 429 + `Retry-After`

## K — Keycloak'a geçiş

R9'dan sonra planlanır: kendi auth'umuzun tag'lenmesi, kaldırılması, Keycloak (compose, realm), resource server'ın Keycloak'a bağlanması.
Her adımda R adımlarıyla karşılaştırma.

## W — Workspace (spec Phase 2, adım 1–3)

- [ ] **W1** Workspace domain: migration `workspaces` + `workspace_members`, entity'ler, create (workspace + ilk OWNER aynı tx), listMine, get
- [ ] **W2** Workspace yetki kuralları: rename / archive, üyelik yönetimi (email ile ekleme), son OWNER invariant'ı (`SELECT … FOR UPDATE`)
- [ ] **W3** Son OWNER concurrency testi (spec §15 istisnası; Testcontainers altyapısı R8'de kurulmuş olur)

## B — Todo Core (spec Phase 1)

- [ ] **B1** `todos` migration + `Todo` entity: status / priority, `completed_at` tutarlılık CHECK'i, same-workspace composite key, `@Version`
- [ ] **B2** Todo oluşturma ve okuma: workspace-scoped sorgular (`todo id + workspace id`), üyelik kontrolü
- [ ] **B3** Todo güncelleme + status geçişleri (DONE ↔ completedAt) + optimistic locking (sürüm çakışmasında 409)
- [ ] **B4** Todo hard delete (yalnız oluşturan ya da OWNER)
- [ ] **B5** Todo listeleme: pagination + sorting

## C — Collaboration ve sorgular (spec Phase 2, adım 4–12)

- [ ] **C1** Projects: migration + create / list / get / update / archive (workspace içinde case-insensitive unique isim)
- [ ] **C2** Todo ↔ Project (opsiyonel; composite FK ile aynı workspace garantisi)
- [ ] **C3** Tags: migration + CRUD (case-insensitive unique, `#RRGGBB` CHECK)
- [ ] **C4** TodoTag: migration + ekle / çıkar; tag silinirken join satırlarının temizliği
- [ ] **C5** TodoAssignee: migration + ata / kaldır; üye çıkarılırken atamaların temizliği (TD-11)
- [ ] **C6** Todo filtreleme: status, priority, project, tag, assignee, due aralığı, başlık/açıklama arama
- [ ] **C7** Performans: N+1 tespiti, fetch plan, `EXPLAIN ANALYZE` ile index kararları

## D — Testler (spec Phase 4A)

- [ ] **D1** Security / yetki matrisi: 401 / 403 / 404 senaryoları (MockMvc + `jwt()` post-processor)
- [ ] **D2** Auth akış testleri: register → login → refresh → reuse → logout (Testcontainers)
- [ ] **D3** Persistence testleri: FK, UNIQUE, CHECK, composite FK, optimistic locking

## E — Production hazırlığı (spec Phase 4B)

- [ ] **E1** OpenAPI (springdoc) + Bearer security scheme + public docs
- [ ] **E2** Structured logging + correlation id + hassas veri maskeleme (+ TD-12)
- [ ] **E3** Actuator: yalnız güvenli health public; metrics temel
- [ ] **E4** Config hardening: `prod` profili, secret'lar env'den (JWT key'leri, DB), refresh cookie `Secure`, forward headers (rate limit IP'si için), graceful shutdown

## F — Docker ve deploy (spec Phase 4C)

- [ ] **F1** Dockerfile: multi-stage build, non-root, `.dockerignore`, arm64 uyumlu
- [ ] **F2** Production compose: app + PostgreSQL + reverse proxy (HTTPS), healthcheck, volume, env dosyası örneği
- [ ] **F3** CI: GitHub Actions ile build + test
- [ ] **F4** ⚠️ Host seçimi ve kurulum: VPS + reverse proxy (HTTPS) ya da PaaS — seçenekler sunulur, karar Onat'ın
  - Öneri (2026-10-23): **Oracle Cloud Always Free**, Ampere A1 ARM VM (4 OCPU / 24 GB RAM / 200 GB disk, süresiz).
    Tek VM'de `docker compose`: app + PostgreSQL + Redis + Kafka (KRaft) + Caddy (Let's Encrypt ile HTTPS); domain DuckDNS ya da ucuz bir domain
    Tuzaklar: kart doğrulaması; popüler region'larda A1 kapasitesi bulunamayabilir (home region sonradan değişmez);
    düşük kullanımlı Always Free VM'ler geri alınabilir (Pay-As-You-Go'ya yükseltmek bunu kaldırır, limit içinde ücret yok);
    image'lar arm64 olmalı (F1); yedek / güncelleme / firewall bizde.
    Elenenler: Render / Koyeb free (512 MB, uyku — Kafka sığmaz), GCP e2-micro (1 GB), AWS / Azure (süreli kredi), Fly.io (free yok), managed Kafka free tier'ları (deneme ya da çok kısıtlı).
- [ ] **F5** ⚠️ Release: `develop` → `main`, `v1.0.0` tag, ilk deploy, public smoke (API, OpenAPI, register / login, health)

## G — Redis / Kafka öğrenme modülleri (spec §2.1, Phase 5) 🔬

v1 deploy edildikten sonra; her biri profile / flag ile kapatılabilir.

- [ ] **G1** Redis: paylaşılan login rate limit (+ Testcontainers testi)
- [ ] **G2** Redis: access token denylist (`jti`, TTL = kalan ömür)
- [ ] **G3** Kafka: `outbox_events` tablosu + relay (KRaft tek broker, compose)
- [ ] **G4** Kafka: `UserDeactivated` → refresh token revoke (idempotent consumer)
- [ ] **G5** Kafka: `WorkspaceMemberRemoved` → atama temizliği
