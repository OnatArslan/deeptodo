# DeepToDo — Roadmap

Hedef: projeyi bugünkü noktadan **public HTTPS'te çalışan v1**'e getirmek (spec §17), sonra Redis / Kafka öğrenme modülleri (spec §2.1).
Kimlik yönetimi **Keycloak** üzerindedir (karar 2026-10-24, spec §10); uygulama yalnız OAuth2 Resource Server'dır.

Kurallar (`CLAUDE.md`): her prompt bir adım, her adım bir PR (~300 satır / 10 dosya), Claude merge eder ve chat'te anlatır.
Keycloak, Redis ve Kafka adımlarında anlatım **derin** yapılır (`CLAUDE.md` → "Derin anlatım").
Adım bitince `[x]` ve PR numarası yazılır. Sıra değişikliği Onat'ın kararıdır.

İşaretler: `[ ]` yapılmadı · `[x]` merge edildi · ⚠️ Onat'ın onayı gerekir · 🔬 derin anlatım

---

## A — Temel ve geçiş

- [x] **A1** Ortam: deeptodo-postgres'in host portunu çakışmayan bir porta taşı (5432'de başka proje var) — `compose.yaml` + `DB_URL` varsayılanı (#2)
- [x] **A2** Hata sözleşmesi: `common/error` (404 / 403 / 409) + `SecurityProblemHandler` (filter zinciri 401 / 403 ProblemDetail) + validation ProblemDetail (#3)
- [x] **A3** Keycloak geçiş planı: ROADMAP, spec v4, derin anlatım kuralı (#4)
- [ ] **A4** Kendi yazdığımız authentication kodunu kaldır: register / login / JWT üretimi / refresh token / rate limit; resource server Keycloak issuer'ına bakar hale gelir

## K — Keycloak (kimlik sağlayıcı) 🔬

- [ ] **K1** Keycloak'ı local'e kur: compose servisi, PostgreSQL'de ayrı `keycloak` database'i, `deeptodo` realm'i (export dosyası repoda, açılışta import), client'lar, test kullanıcıları.
  Anlatım: realm / client / user / role modeli, OIDC discovery, authorization code + PKCE akışı, token'ın anatomisi.
- [ ] **K2** Resource server'ı Keycloak'a bağla: `issuer-uri`, JWKS ile imza doğrulama, `aud` (audience) doğrulaması, key rotation.
  Anlatım: token doğrulamanın her adımı, issuer uyuşmazlığı tuzağı (localhost vs container adı).
- [ ] **K3** Yerel kullanıcı eşleme: migration (`users.auth_subject` eklenir, `password_hash` ve `refresh_tokens` kaldırılır),
  ilk istekte just-in-time provisioning, `CurrentUser` (`sub` → yerel user id), DEACTIVATED kullanıcının reddi.
  ⚠️ Local DB volume'ünün sıfırlanmasını gerektirebilir (commit edilmemiş workspace migration'ı V4'ten V5'e kayar).
- [ ] **K4** Hesap: `GET / PATCH / DELETE /api/users/me`. DELETE → yerel DEACTIVATED + Keycloak Admin API ile kullanıcıyı disable et ve session'larını kapat
  (service account, client credentials). Admin çağrısı DB transaction'ının dışında; dual-write riski bilinçli olarak not edilir (G4'te outbox ile çözülür).
- [ ] **K5** Keycloak güvenlik ayarları (realm export'ta): brute force detection, password policy, token ömürleri, refresh token rotation + reuse.
  Anlatım: kaldırdığımız kodla birebir karşılaştırma (rate limiter, rotation, reuse detection).

## W — Workspace (spec Phase 2, adım 1–3)

- [ ] **W1** Workspace domain: migration `workspace_members`, entity'ler, create (workspace + ilk OWNER aynı tx), listMine, get
- [ ] **W2** Workspace yetki kuralları: rename / archive, üyelik yönetimi (email ile ekleme), son OWNER invariant'ı (`SELECT … FOR UPDATE`)
- [ ] **W3** Test altyapısı (Testcontainers PostgreSQL) + son OWNER concurrency testi (spec §15 istisnası)

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
- [ ] **D2** Keycloak entegrasyon testi: Testcontainers ile gerçek Keycloak'tan token alıp API çağırma 🔬
- [ ] **D3** Persistence testleri: FK, UNIQUE, CHECK, composite FK, optimistic locking

## E — Production hazırlığı (spec Phase 4B)

- [ ] **E1** OpenAPI (springdoc) + Swagger UI'da Keycloak ile OAuth2 login (authorization code + PKCE) 🔬
- [ ] **E2** Structured logging + correlation id + hassas veri maskeleme (+ TD-12)
- [ ] **E3** Actuator: yalnız güvenli health public; metrics temel
- [ ] **E4** Config hardening: `prod` profili, secret'lar env'den (Keycloak client secret, DB), forward headers, graceful shutdown

## F — Docker ve deploy (spec Phase 4C)

- [ ] **F1** Dockerfile: multi-stage build, non-root, `.dockerignore`, arm64 uyumlu
- [ ] **F2** Production compose: app + PostgreSQL (app ve keycloak database'leri) + Keycloak (prod mode, `hostname`, proxy header'ları) + reverse proxy 🔬
- [ ] **F3** CI: GitHub Actions ile build + test
- [ ] **F4** ⚠️ Host seçimi ve kurulum: VPS + reverse proxy (HTTPS) ya da PaaS — seçenekler sunulur, karar Onat'ın
  - Öneri (2026-10-23): **Oracle Cloud Always Free**, Ampere A1 ARM VM (4 OCPU / 24 GB RAM / 200 GB disk, süresiz).
    Tek VM'de `docker compose`: app + PostgreSQL + Keycloak + Redis + Kafka (KRaft) + Caddy (Let's Encrypt ile HTTPS); domain DuckDNS ya da ucuz bir domain
    (`api.` ve `auth.` alt alan adları).
    Tuzaklar: kart doğrulaması; popüler region'larda A1 kapasitesi bulunamayabilir (home region sonradan değişmez);
    düşük kullanımlı Always Free VM'ler geri alınabilir (Pay-As-You-Go'ya yükseltmek bunu kaldırır, limit içinde ücret yok);
    image'lar arm64 olmalı (F1); yedek / güncelleme / firewall bizde.
    Elenenler: Render / Koyeb free (512 MB, uyku — Kafka sığmaz), GCP e2-micro (1 GB), AWS / Azure (süreli kredi), Fly.io (free yok), managed Kafka free tier'ları (deneme ya da çok kısıtlı).
- [ ] **F5** ⚠️ Release: `develop` → `main`, `v1.0.0` tag, ilk deploy, public smoke (API, OpenAPI, Keycloak login, health)

## G — Redis / Kafka öğrenme modülleri (spec §2.1, Phase 5) 🔬

v1 deploy edildikten sonra; her biri profile / flag ile kapatılabilir.

- [ ] **G1** Redis: kullanıcı eşleme cache'i (`sub` → yerel user id + status). K3'ten sonra her istek bir DB sorgusu yapar; cache bunu kaldırır, deactivation'da invalidation (+ Testcontainers testi)
- [ ] **G2** Redis: kullanıcı başına API rate limit (yazma endpoint'leri, 429 + `Retry-After`, `INCR` + `EXPIRE`)
- [ ] **G3** Kafka: `outbox_events` tablosu + relay (KRaft tek broker, compose)
- [ ] **G4** Kafka: `UserDeactivated` → Keycloak'ta disable + logout. K4'teki doğrudan Admin API çağrısı outbox'a taşınır (dual-write çözümü, idempotent consumer)
- [ ] **G5** Kafka: `WorkspaceMemberRemoved` → atama temizliği
