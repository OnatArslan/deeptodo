# DeepToDo — Roadmap

Hedef: projeyi bugünkü noktadan **public HTTPS'te çalışan v1**'e getirmek (spec §17), sonra Redis / Kafka öğrenme modülleri (spec §2.1).
Authentication uygulamada, Spring Security ile **sıfırdan, adım adım** yeniden kurulur (karar 2026-10-25, spec §10).

Kurallar (`CLAUDE.md`): her prompt bir adım, her adım bir PR (~300 satır / 10 dosya), Claude merge eder ve chat'te anlatır.
🔬 işaretli adımlarda anlatım **derin** yapılır (`CLAUDE.md` → "Derin anlatım").
Adım bitince `[x]` ve PR numarası yazılır. Sıra değişikliği Onat'ın kararıdır.

İşaretler: `[ ]` yapılmadı · `[x]` merge edildi · ⚠️ Onat'ın onayı gerekir · 🔬 derin anlatım

---

## A — Temel

- [x] **A1** Ortam: deeptodo-postgres'in host portunu çakışmayan bir porta taşı (5432'de başka proje var) (#2)
- [x] **A2** Hata sözleşmesi: `common/error` (404 / 403 / 409) + filter zinciri 401 / 403 ProblemDetail + validation ProblemDetail (#3, #6)
- [x] **A3** Auth sıfırlama planı: ROADMAP, spec v5, CLAUDE.md (#9)
- [x] **A4** (#10) Auth sıfırlama: Keycloak (compose, realm, database), `auth` paketinin tamamı, security dependency'leri ve `/api/users/me` kaldırılır.
  DB şeması (`users.password_hash`, `refresh_tokens`) olduğu gibi kalır; yeniden kurulumda kullanılır

Geçmiş (bırakılan yol, kayıt için): Keycloak'a geçiş planı (#4), kendi auth kodunun kaldırılması (#5), Keycloak local kurulum (#7),
Keycloak resource server sıkılaştırma (#8). Kod git geçmişinde; kavramlar (`kid`, `iss` / `aud`, key rotation) R adımlarında kullanılır.
Commit edilmemiş eski user / workspace kodu `wip/user-workspace-reference` branch'inde referans olarak duruyor.

## R — Authentication (spec §10, Phase 3) 🔬

- [ ] **R1** Spring Security'yi ekle: dependency'nin tek başına yaptığı (her şey kilitli, üretilmiş parola), sonra kendi `SecurityFilterChain`'imiz:
  stateless, CSRF kararı, health açık, 401 / 403 ProblemDetail (`SecurityProblemHandler`). Filter zincirini TRACE log ile gözlemleme
- [ ] **R2** Parola + register: `PasswordEncoder` (delegating, bcrypt), `User.passwordHash`, `POST /api/auth/register` (email normalize, tekil → 409)
- [ ] **R3** Login: `AuthenticationManager` → `DaoAuthenticationProvider` → `UserDetailsService`; timing attack koruması; yanlış parola / bilinmeyen email aynı 401
- [ ] **R4** Access token: RS256 key çifti (`secrets/`), `JwtEncoder`, claim'ler (`iss`, `sub`, `aud`, `iat`, `exp`, `jti`), header'da `kid`; login token döner
- [ ] **R5** Resource server: public key ile `JwtDecoder`, `iss` / `aud` / `exp` doğrulaması, `CurrentUser`, `GET /api/users/me`, DEACTIVATED kullanıcının reddi
- [ ] **R6** Key rotation: birden çok key, `kid` ile seçim, eski key'le imzalı token'ların geçiş süresi
- [ ] **R7** Refresh token: opaque 256-bit, DB'de SHA-256 hash (V3 tablosu), HttpOnly + SameSite=Strict + Path cookie; login / register'da üretim
- [ ] **R8** Rotation: `POST /api/auth/refresh`, koşullu `markUsed` + Testcontainers altyapısı ve eşzamanlı refresh testi (spec §15 istisnası)
- [ ] **R9** Reuse detection: kullanılmış token tekrar gelirse ailenin revoke'u; reddetmeyi sonuç nesnesiyle dönmek ve transaction sınırı
- [ ] **R10** Logout + hesap: `POST /api/auth/logout`, `PATCH / DELETE /api/users/me`; hesap kapatınca bütün refresh token'ların revoke'u
- [ ] **R11** Login rate limit: in-memory, email + IP, 429 + `Retry-After`

## W — Workspace (spec Phase 2, adım 1–3)

- [ ] **W1** Workspace domain: migration `workspace_members`, entity'ler, create (workspace + ilk OWNER aynı tx), listMine, get
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
