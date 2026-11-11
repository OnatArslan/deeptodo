# DeepToDo — Project Specification v6

> v6 (2026-11-11): proje küçültüldü. Workspace, üyelik, atama ve rol yok; her kullanıcı yalnız kendi verisini yönetir.
> Önceki spec (v5, workspace tabanlı) git geçmişinde: `git show 529a964:PROJECT_SPEC.md`.

## 1. Amaç

Onat'ın Spring Boot, Spring Data JPA ve Spring Security'yi **kendi yazdığı** bir projeyle gösterebilmesi. Kodun tamamını
Onat yazar; Claude adımları tanımlar ve review eder (`CLAUDE.md`).

Proje küçük ama her parçası "production'da nasıl yapılır" sorusuna doğru cevap verir: şema Flyway'de ve DB constraint'leriyle
korunur, yetki sorgu seviyesindedir, auth standartlara uygundur, kritik akışlar gerçek PostgreSQL'e karşı test edilir.

## 2. Ürün

Kişisel görev yönetimi REST API'si.

- Kullanıcı kaydolur, giriş yapar, oturumu yenilenir, çıkış yapar, hesabını kapatabilir.
- Kendi todo'larını oluşturur, listeler (sayfalı, sıralı, filtreli), günceller, siler.
- Todo'ları isteğe bağlı olarak bir project altında toplar; todo'lara tag ekler.
- Başka bir kullanıcının verisi hiçbir şekilde görünmez; var olduğu da belli olmaz (404).

### Kapsam dışı

Workspace / paylaşım / üyelik / atama, roller ve admin, e-posta doğrulama ve parola sıfırlama, dosya eki, bildirim,
frontend, microservice. Keycloak, Redis, Kafka ve public deploy yalnız ROADMAP'in opsiyonel bölümünde, ana hedef bittikten sonra.

## 3. Teknoloji

| Alan | Seçim |
|---|---|
| Dil / build | Java 21, Maven |
| Framework | Spring Boot 4.1 (Framework 7, Security 7) |
| Web | Spring Web MVC, Bean Validation, `ProblemDetail` |
| Persistence | PostgreSQL 17, Flyway, Spring Data JPA (Hibernate 7) |
| Security | Spring Security, OAuth2 Resource Server (JWT, RS256), Argon2id |
| Test | JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL) |
| Dokümantasyon / paket | springdoc-openapi, Docker (multi-stage) |

## 4. Kurallar (bütün tablolar)

- Şemanın kaynağı Flyway; Hibernate yalnız doğrular (`ddl-auto: validate`).
- Tablo adları çoğul `snake_case`. ID `uuid`, UUID v7, uygulama üretir (DB default'u yok).
- Zaman `Instant` / `timestamptz`. Enum'lar string olarak saklanır, izin verilen değerler DB'de CHECK ile korunur.
- Constraint adları: `pk_<tablo>`, `uq_<tablo>_<anlam>`, `fk_<tablo>_<anlam>`, `ck_<tablo>_<anlam>`.
- Java'da kontrol edilen kural DB'de de durur (UNIQUE, CHECK, FK); DB son savunma hattıdır.
- Uygulanmış bir migration değiştirilmez; düzeltme yeni migration'dır.

## 5. Şema

### `users`

| Kolon | Tip | Null | Not |
|---|---|:-:|---|
| `id` | `uuid` | hayır | PK |
| `email` | `varchar(320)` | hayır | lowercase saklanır, UNIQUE |
| `display_name` | `varchar(100)` | hayır | boş olamaz |
| `password_hash` | `varchar(255)` | hayır | auth adımında eklenir; düz parola asla saklanmaz |
| `status` | `varchar(20)` | hayır | `ACTIVE`, `DEACTIVATED` |
| `created_at`, `updated_at` | `timestamptz` | hayır | |

Kullanıcı silinmez, kapatılır (`DEACTIVATED`): login ve refresh yapamaz, bütün refresh token'ları revoke edilir.

### `refresh_tokens`

| Kolon | Tip | Null | Not |
|---|---|:-:|---|
| `id` | `uuid` | hayır | PK |
| `user_id` | `uuid` | hayır | FK → users |
| `family_id` | `uuid` | hayır | bir login = bir aile (oturum) |
| `token_hash` | `varchar(64)` | hayır | SHA-256 hex, UNIQUE; ham token saklanmaz |
| `created_at`, `expires_at` | `timestamptz` | hayır | `expires_at > created_at` |
| `used_at`, `revoked_at` | `timestamptz` | evet | |

### `projects`

| Kolon | Tip | Null | Not |
|---|---|:-:|---|
| `id` | `uuid` | hayır | PK |
| `owner_id` | `uuid` | hayır | FK → users |
| `name` | `varchar(120)` | hayır | boş olamaz; aynı sahipte case-insensitive UNIQUE |
| `description` | `varchar(1000)` | evet | |
| `status` | `varchar(20)` | hayır | `ACTIVE`, `ARCHIVED` |
| `created_at`, `updated_at` | `timestamptz` | hayır | |

UNIQUE `(id, owner_id)`: todo'nun project'i aynı sahibe ait olsun diye composite FK bunu referans alır.
Project silinmez, arşivlenir; arşivlenmiş project'e yeni todo eklenmez.

### `todos`

| Kolon | Tip | Null | Not |
|---|---|:-:|---|
| `id` | `uuid` | hayır | PK |
| `owner_id` | `uuid` | hayır | FK → users |
| `project_id` | `uuid` | evet | `(project_id, owner_id)` → `projects(id, owner_id)` |
| `title` | `varchar(200)` | hayır | boş olamaz |
| `description` | `varchar(2000)` | evet | |
| `status` | `varchar(20)` | hayır | `TODO`, `IN_PROGRESS`, `DONE`, `CANCELLED`; yeni todo `TODO` |
| `priority` | `varchar(20)` | hayır | `LOW`, `MEDIUM`, `HIGH`, `URGENT`; yeni todo `MEDIUM` |
| `due_at` | `timestamptz` | evet | geçmiş olabilir |
| `completed_at` | `timestamptz` | evet | yalnız `DONE` iken dolu (CHECK) |
| `created_at`, `updated_at` | `timestamptz` | hayır | |
| `version` | `bigint` | hayır | optimistic locking |

`DONE` olunca `completed_at` yazılır, `DONE`'dan çıkınca temizlenir. Todo hard delete edilir (önce tag bağlantıları,
aynı transaction'da).

### `tags`

| Kolon | Tip | Null | Not |
|---|---|:-:|---|
| `id` | `uuid` | hayır | PK |
| `owner_id` | `uuid` | hayır | FK → users |
| `name` | `varchar(50)` | hayır | boş olamaz; aynı sahipte case-insensitive UNIQUE |
| `color` | `varchar(7)` | evet | `#RRGGBB` (CHECK) |
| `created_at`, `updated_at` | `timestamptz` | hayır | |

UNIQUE `(id, owner_id)`. Tag hard delete edilir; bağlantıları aynı transaction'da kalkar, todo'lar silinmez.

### `todo_tags`

Todo ↔ Tag N:N ilişkisinin açık join entity'si (`@ManyToMany` kullanılmaz).

| Kolon | Tip | Null | Not |
|---|---|:-:|---|
| `id` | `uuid` | hayır | PK |
| `owner_id` | `uuid` | hayır | |
| `todo_id` | `uuid` | hayır | `(todo_id, owner_id)` → `todos(id, owner_id)` |
| `tag_id` | `uuid` | hayır | `(tag_id, owner_id)` → `tags(id, owner_id)` |
| `added_at` | `timestamptz` | hayır | |

UNIQUE `(todo_id, tag_id)`. `todos` için de UNIQUE `(id, owner_id)` gerekir.

## 6. API

Bütün uçlar JSON; hatalar `application/problem+json` (`ProblemDetail`). `/api/auth/register`, `/login`, `/refresh` ve
`/actuator/health` dışında her uç `Authorization: Bearer <access token>` ister.

```text
POST   /api/auth/register          201  kullanıcı oluşturur, token dönmez
POST   /api/auth/login             200  access token (gövde) + refresh token (cookie)
POST   /api/auth/refresh           200  rotation: yeni access + yeni refresh cookie
POST   /api/auth/logout            204  ailenin revoke'u, cookie silinir

GET    /api/users/me               200
DELETE /api/users/me               204  hesap kapatma

POST   /api/todos                  201  + Location
GET    /api/todos                  200  sayfalı; filtre + sıralama (§8)
GET    /api/todos/{id}             200
PATCH  /api/todos/{id}             200  kısmi güncelleme; status geçişleri; version çakışması 409
DELETE /api/todos/{id}             204
PUT    /api/todos/{id}/tags/{tagId}    204  idempotent ekleme
DELETE /api/todos/{id}/tags/{tagId}    204

POST   /api/projects               201
GET    /api/projects               200  (?status=ACTIVE|ARCHIVED)
GET    /api/projects/{id}          200
PATCH  /api/projects/{id}          200  isim / açıklama
POST   /api/projects/{id}/archive  204

POST   /api/tags                   201
GET    /api/tags                   200
PATCH  /api/tags/{id}              200
DELETE /api/tags/{id}              204
```

## 7. Authentication

- **Parola:** Argon2id (yeni hash'ler), `DelegatingPasswordEncoder` ile ileride algoritma değiştirilebilir.
- **Register:** email normalize (lowercase), tekil email → 409 (yarışta DB UNIQUE yakalanır).
- **Login:** `AuthenticationManager` → `DaoAuthenticationProvider` → `UserDetailsService`. Bilinmeyen email, yanlış parola
  ve kapalı hesap aynı 401'i döner.
- **Access token:** JWT, RS256, 15 dk. Header'da `kid`. Claim'ler `iss`, `sub` (user id), `aud`, `iat`, `exp`, `jti`;
  rol / email / yetki bilgisi yok. Doğrulama Spring Security Resource Server ile (`iss`, `aud`, `exp`), DB'ye gitmez.
  Key'ler repoda değil: local'de `secrets/`, production'da env.
- **Refresh token:** opaque 256-bit, DB'de SHA-256 hash, 14 gün. Cookie: `HttpOnly`, `SameSite=Strict`,
  `Path=/api/auth`, production'da `Secure`. Her refresh tek kullanımlık (koşullu UPDATE), halef aynı ailede.
  Kullanılmış token tekrar gelirse aile revoke edilir (reuse detection). CSRF kapalı; cookie'li uçları SameSite korur.

## 8. Yetki ve sorgular

- Yetki rol değil sahipliktir: her sorgu `id + owner_id` ile çalışır. Başkasının kaydı → 404 (403 değil; varlığı sızmaz).
- Todo listesi: sayfalı (varsayılan 20, en fazla 100), sıralama `createdAt` / `dueAt` / `priority`; filtreler `status`,
  `priority`, `projectId`, `tagId`, `dueBefore` / `dueAfter`, `q` (başlık / açıklamada arama).
- Liste sorgularında N+1 olmaz; sorgu sayısı ölçülerek gösterilir.

## 9. Hata sözleşmesi

| Durum | Status |
|---|---|
| Validation, bozuk JSON | 400 |
| Kimlik yok / geçersiz token / login başarısız | 401 (`WWW-Authenticate`) |
| Kayıt yok ya da başkasının | 404 |
| Tekillik ihlali, arşivlenmiş project'e todo, version çakışması | 409 |
| Beklenmeyen hata | 500 (iç detay sızmaz) |

Domain exception'ları HTTP bilmez; eşleme `@RestControllerAdvice`'ta.

## 10. Test

Testcontainers ile gerçek PostgreSQL'e karşı:
- auth akışı: register → login → `/me` → refresh → eski token reuse → aile revoke → logout
- sahiplik: başka kullanıcının todo / project / tag'i 404
- eşzamanlılık: aynı refresh token'la paralel istekler, yalnız biri kazanır
- DB constraint'leri: `completed_at` CHECK, composite FK (başkasının project'i), tekillikler
- optimistic locking: aynı version ile iki güncelleme, ikincisi 409

## 11. Bitti tanımı (v1)

`docker compose up` ile app + PostgreSQL açılır; OpenAPI arayüzünden bütün uçlar denenebilir; §10'daki testler yeşil;
README projeyi çalıştırmayı ve tasarım kararlarını anlatır.
