# DeepToDo — Roadmap

Hedef: projeyi bugünkü noktadan **public HTTPS'te çalışan v1**'e getirmek (spec §17), sonra Redis / Kafka öğrenme modülleri (spec §2.1).

Kurallar (`CLAUDE.md`): her prompt bir adım, her adım bir PR (~300 satır / 10 dosya), Claude merge eder ve chat'te anlatır.
Adım bitince `[x]` ve PR numarası yazılır. Sıra değişikliği Onat'ın kararıdır.

İşaretler: `[ ]` yapılmadı · `[x]` merge edildi · ⚠️ Onat'ın onayı gerekir

---

## A — Mevcut işi yerleştir

Bugün commit edilmemiş duran auth tamamlama ve workspace işi, okunabilir PR'lara bölünerek `develop`'a alınır.

- [x] **A1** Ortam: deeptodo-postgres'in host portunu çakışmayan bir porta taşı (5432'de başka proje var) — `compose.yaml` + `DB_URL` varsayılanı (#2)
- [x] **A2** Hata sözleşmesi: `common/error` (404 / 403 / 409) + `SecurityProblemHandler` (filter zinciri 401 / 403 ProblemDetail) + validation ProblemDetail (#3)
- [ ] **A3** Hesap yönetimi: `CurrentUser`, `PATCH/DELETE /api/users/me`, hesap kapatınca bütün refresh token'ların revoke'u
- [ ] **A4** Login rate limit (in-memory, email + IP, 429 + `Retry-After`)
- [ ] **A5** Workspace domain: V4 `workspace_members`, entity'ler, create (workspace + ilk OWNER aynı tx), listMine, get
- [ ] **A6** Workspace yetki kuralları: rename / archive, üyelik yönetimi, son OWNER invariant'ı (`SELECT … FOR UPDATE`)

## B — Todo Core (spec Phase 1)

- [ ] **B1** V5 `todos` + `Todo` entity: status / priority, `completed_at` tutarlılık CHECK'i, same-workspace composite key, `@Version`
- [ ] **B2** Todo oluşturma ve okuma: workspace-scoped sorgular (`todo id + workspace id`), üyelik kontrolü
- [ ] **B3** Todo güncelleme + status geçişleri (DONE ↔ completedAt) + optimistic locking (sürüm çakışmasında 409)
- [ ] **B4** Todo hard delete (yalnız oluşturan ya da OWNER)
- [ ] **B5** Todo listeleme: pagination + sorting

## C — Collaboration ve sorgular (spec Phase 2, adım 4–12)

- [ ] **C1** Projects: V6 + create / list / get / update / archive (workspace içinde case-insensitive unique isim)
- [ ] **C2** Todo ↔ Project (opsiyonel; composite FK ile aynı workspace garantisi)
- [ ] **C3** Tags: V7 + CRUD (case-insensitive unique, `#RRGGBB` CHECK)
- [ ] **C4** TodoTag: V8 + ekle / çıkar; tag silinirken join satırlarının temizliği
- [ ] **C5** TodoAssignee: V9 + ata / kaldır; üye çıkarılırken atamaların temizliği (TD-11)
- [ ] **C6** Todo filtreleme: status, priority, project, tag, assignee, due aralığı, başlık/açıklama arama
- [ ] **C7** Performans: N+1 tespiti, fetch plan, `EXPLAIN ANALYZE` ile index kararları

## D — Testler (spec Phase 4A)

- [ ] **D1** Test altyapısı: Testcontainers PostgreSQL + ilk integration test
- [ ] **D2** Concurrency testleri: refresh rotation / reuse ve son OWNER invariant'ı (TD-8)
- [ ] **D3** Security / yetki matrisi: 401 / 403 / 404 senaryoları (MockMvc)
- [ ] **D4** Persistence testleri: FK, UNIQUE, CHECK, composite FK, optimistic locking

## E — Production hazırlığı (spec Phase 4B)

- [ ] **E1** OpenAPI (springdoc) + Bearer security scheme + public docs
- [ ] **E2** Structured logging + correlation id + hassas veri maskeleme
- [ ] **E3** Actuator: yalnız güvenli health public; metrics temel
- [ ] **E4** Config hardening: `prod` profili, secret'lar env'den (JWT key'leri dahil), `REFRESH_COOKIE_SECURE=true`, forward headers (TD-9, TD-10), graceful shutdown

## F — Docker ve deploy (spec Phase 4C)

- [ ] **F1** Dockerfile: multi-stage build, non-root, `.dockerignore`
- [ ] **F2** Production compose: app + postgres, healthcheck, volume, env dosyası örneği
- [ ] **F3** CI: GitHub Actions ile build + test
- [ ] **F4** ⚠️ Host seçimi ve kurulum: VPS + reverse proxy (HTTPS) ya da PaaS — seçenekler sunulur, karar Onat'ın
  - Öneri (2026-10-23): **Oracle Cloud Always Free**, Ampere A1 ARM VM (4 OCPU / 24 GB RAM / 200 GB disk, süresiz).
    Tek VM'de `docker compose`: app + PostgreSQL + Redis + Kafka (KRaft) + Caddy (Let's Encrypt ile HTTPS); domain DuckDNS ya da ucuz bir domain.
    Tuzaklar: kart doğrulaması; popüler region'larda A1 kapasitesi bulunamayabilir (home region sonradan değişmez);
    düşük kullanımlı Always Free VM'ler geri alınabilir (Pay-As-You-Go'ya yükseltmek bunu kaldırır, limit içinde ücret yok);
    image'lar arm64 olmalı (F1); yedek / güncelleme / firewall bizde.
    Elenenler: Render / Koyeb free (512 MB, uyku — Kafka sığmaz), GCP e2-micro (1 GB), AWS / Azure (süreli kredi), Fly.io (free yok), managed Kafka free tier'ları (deneme ya da çok kısıtlı).
- [ ] **F5** ⚠️ Release: `develop` → `main`, `v1.0.0` tag, ilk deploy, public smoke (API, OpenAPI, health)

## G — Redis / Kafka öğrenme modülleri (spec §2.1, Phase 5)

v1 deploy edildikten sonra; her biri profile / flag ile kapatılabilir.

- [ ] **G1** Redis: paylaşılan login rate limit (+ Testcontainers testi)
- [ ] **G2** Redis: access token denylist (`jti`, TTL = kalan ömür)
- [ ] **G3** Kafka: `outbox_events` tablosu + relay (KRaft tek broker, compose)
- [ ] **G4** Kafka: `UserDeactivated` → auth token revoke (idempotent consumer)
- [ ] **G5** Kafka: `WorkspaceMemberRemoved` → atama temizliği
