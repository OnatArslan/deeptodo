# DeepToDo — Project Specification v4

## 1. Proje amacı

`deeptodo`, workspace tabanlı collaborative task management backend uygulamasıdır.

Bu projenin birinci amacı çalışan bir CRUD API üretmek değil; aynı codebase üzerinde aşağıdaki konuları gerçek bir backend sistemi içinde uygulayarak öğrenmektir:

- modern Java
- Spring Boot
- Spring MVC
- Spring Data JPA / Hibernate
- PostgreSQL ve schema design
- transaction boundaries
- concurrency
- Spring Security (OAuth2 Resource Server)
- Keycloak (kimlik sağlayıcı, OIDC; kendi container'ı, PostgreSQL'de ayrı database)
- authentication / authorization (OAuth2 / OIDC, Keycloak)
- backend testing
- logging / health / metrics
- Docker build ve public deployment
- Redis (paylaşılan state, TTL'li veri) ve Kafka (domain event'leri, transactional outbox) — §2.1 öğrenme modülleri

Proje agentic geliştirilir: kodu Claude küçük, öğretici PR'larla yazar; Onat yönlendirir, review eder ve merge eder (bkz. `CLAUDE.md`).

Proje **scope kontrollü** tutulur. Kursta öğrenilen her teknolojiyi DeepToDo'ya eklemek hedef değildir.

Ana ilke:

> Kurslar breadth öğretir; DeepToDo yalnız ürünün gerçekten ihtiyaç duyduğu konuları kaliteli şekilde uygular.

## 2. V1 ürün sınırı

### Dahil

- User registration / login — Keycloak üzerinden (OIDC); uygulama kendi login'ini yazmaz
- Current user profile görüntüleme ve display-name update
- User account deactivation
- Workspace oluşturma, görüntüleme, rename ve archive
- Workspace membership
- `OWNER` ve `MEMBER` rolleri
- Bir workspace'te birden fazla OWNER
- Son OWNER invariant'ı
- Workspace içinde Project
- Project create/read/update/archive
- Workspace-level veya Project'e bağlı Todo
- Todo status / priority / due date
- Todo hard delete
- Workspace-level Tag
- Todo ↔ Tag explicit join entity
- Todo ↔ User multi-assignee explicit join entity
- Pagination / sorting / filtering
- Basit title/description search
- Keycloak'ın ürettiği JWT access token'ın Resource Server olarak doğrulanması
- Refresh token rotation / reuse ve login brute-force koruması — Keycloak realm ayarları olarak
- Keycloak kullanıcısının yerel `users` satırına eşlenmesi (just-in-time provisioning)
- Resource-based authorization
- Public OpenAPI documentation
- Final aşamada Docker image + public HTTPS deployment

### Kapsam dışında

- PERSONAL / TEAM workspace ayrımı
- `ADMIN` role
- automatic personal workspace
- invitation/email workflow
- email verification, forgot-password, MFA (Keycloak'ta birer ayar; SMTP gerektirdikleri için v1'de kapalı)
- uygulama içinde kendi password / token / refresh token kodu
- microservices
- CQRS / event sourcing
- WebSocket / SSE
- file attachment
- friendship/social features
- AuditLog table
- full-text search engine
- Elasticsearch/OpenSearch
- Kubernetes
- advanced tracing/dashboard/alerting
- generic BaseService/BaseRepository
- Clean/Hexagonal architecture ceremony without a concrete need

Bu liste ancak gerçek ürün/öğrenme ihtiyacı oluşursa bilinçli karar ile değiştirilir.

### 2.1 Öğrenme modülleri — Redis ve Kafka (karar 2026-10-23, Keycloak'a göre güncellendi 2026-10-24)

Redis ve Kafka, uygulama tek instance çalıştığı sürece **zorunlu değildir**; öğrenme amacıyla, kodda zaten var olan
somut bir problemi çözen yerlere eklenir. Her use-case ayrı, küçük bir PR'dır. Listede olmayan bir yere eklenmez.

Redis:
- **Kullanıcı eşleme cache'i:** her authenticated istek token'daki `sub`'ı yerel kullanıcıya çevirir (DB sorgusu). Sonuç (`user id` + `status`) Redis'te TTL ile tutulur; deactivation'da invalidation. Bayat veri riski (deactivate edilmiş kullanıcının TTL boyunca geçmesi) bu use-case'in öğrenme konusudur.
- **Kullanıcı başına API rate limit:** yazma endpoint'lerinde `INCR` + `EXPIRE` sayaçları, 429 + `Retry-After`; birden çok instance aynı sayacı görür.

Kafka:
- **Transactional outbox:** domain event'leri iş verisiyle aynı transaction'da `outbox_events` tablosuna yazılır; ayrı bir relay bunları Kafka'ya yayınlar (dual-write problemi yok).
- **`UserDeactivated` event'i:** consumer Keycloak Admin API ile kullanıcıyı disable eder ve session'larını kapatır. Hesap kapatmadaki "yerel DB + Keycloak" dual-write'ı böylece çözülür.
- **`WorkspaceMemberRemoved` event'i:** TodoAssignee geldiğinde, çıkarılan üyenin atamalarını temizler.
- Consumer'lar idempotent'tir (at-least-once teslimat varsayımı).

Sınırlar:
- Uygulama yine tek deployable modular monolith'tir; Kafka modüller arası iletişim içindir, microservice'e bölünme yoktur.
- Kafka KRaft modunda tek broker (ZooKeeper yok). Redis tek node.
- Phase 4 deploy'da Redis ve Kafka'nın maliyeti (RAM) ayrıca değerlendirilir; gerekirse production'da kapatılabilir olmalıdır (feature flag / profile).

## 3. Teknoloji baseline

- Java 21+
- Spring Boot 4.1.x stable line
- Maven Wrapper
- PostgreSQL
- Flyway
- Spring Data JPA / Hibernate
- Spring Security
- Bean Validation
- Spring Boot Actuator
- Micrometer basics
- Docker / Docker Compose
- Testing phase'inde JUnit, Mockito, MockMvc ve PostgreSQL Testcontainers
- Öğrenme modülleri (§2.1): Redis (Spring Data Redis), Apache Kafka (Spring for Apache Kafka), Redis / Kafka Testcontainers

Exact patch/library version implementation zamanında compatibility ve official documentation ile doğrulanır.

## 4. Mimari yaklaşım

Uygulama bir **modular monolith** ve **package-by-feature** backend'dir.

Başlangıç akışı:

```text
HTTP
→ Controller
→ Application/Service
→ Repository
→ PostgreSQL
```

### Sorumluluklar

**Controller**
- HTTP request/response contract
- request binding
- validation trigger
- status code
- API DTO

**Application / Service**
- use-case orchestration
- authorization
- transaction boundary
- cross-entity business rules
- multi-repository coordination

**Domain / Entity**
- entity'nin kendi state transition kuralları
- invalid state'i mümkün olduğunca engelleme
- collection/helper davranışı gerçekten gerekiyorsa sahip olma

**Repository**
- persistence/query access
- application business rule taşımama

**PostgreSQL**
- sadece storage değildir
- referential integrity
- uniqueness
- nullability
- state consistency
- concurrency-safe invariant'ların son savunma hattıdır

Hexagonal/Clean Architecture başlangıç şartı değildir.

## 5. Package structure

Feature gerçekten oluştuğunda package oluşturulur; boş package ağacı baştan üretilmez.

Her feature aynı iskeleti izler:

```text
<feature>/
├── <Feature>Service.java   # feature'ın dışa açık kapısı; use-case + transaction boundary
├── web/                    # HTTP katmanı
│   ├── <Feature>Controller.java
│   ├── <Feature>Mapper.java    # entity → DTO; ürettiği DTO'nun yanında
│   └── dto/                    # request/response DTO'ları
├── domain/                 # entity, enum, Spring Data repository
└── exception/              # feature'a özel exception'lar (ihtiyaç doğunca)
```

Uygulama genelinde:

```text
com.onatarslan.deeptodo
├── common
│   ├── config      # cross-cutting bean/config
│   └── error       # global exception handler, API error contract
├── auth            # SecurityFilterChain (resource server), token → yerel kullanıcı eşleme, CurrentUser, Keycloak Admin client
├── user
├── workspace
├── project
├── todo
└── tag
```

Kurallar:
- Başka bir feature yalnız `<Feature>Service` üzerinden kullanılır; başka feature'ın repository'sine doğrudan erişilmez. Java'da alt paket görünürlüğü olmadığı için bu kural derleyiciyle değil review ile korunur.
- Repository ayrı `persistence` paketinde değil, `domain/` içindedir.
- Security kodu `common`'da değil, `auth` feature'ındadır (`auth/config/SecurityConfig` dahil). Workspace/resource authorization ise ilgili feature service'inde, sorgu seviyesinde uygulanır (§9).
- Feature'a özel `config/` paketi ancak somut feature-specific configuration (ör. `@ConfigurationProperties`) oluştuğunda açılır.
- `exception/` içindeki domain/business exception'lar HTTP tipi taşımaz (bkz. §13); HTTP status eşlemesi `common/error`'dadır.
- `messaging/` / domain event paketi yoktur; modüller arası event ihtiyacı somut bir akışla ortaya çıkarsa bilinçli karar ile eklenir.
- Tek `<Feature>Service` belirgin şekilde büyürse use-case bazında bölünmesi ayrıca değerlendirilir.

`common` feature-specific kod çöplüğü değildir.

## 6. Global persistence conventions

### Table naming

Plural `snake_case`:

```text
users
workspaces
workspace_members
projects
todos
tags
todo_tags
todo_assignees
refresh_tokens
```

### ID strategy

Application/domain ID type:

```text
Java: UUID
PostgreSQL: uuid
```

Tercih edilen generation strategy:

- UUID v7
- application/Hibernate tarafında üretilir
- project-managed Hibernate version destekliyorsa modern Hibernate UUID v7 generator kullanılır
- database UUID generation fonksiyonuna bağımlı olunmaz
- bütün entity'lerde tek strategy kullanılır

### Time

Gerçek zaman noktaları:

```text
Java: Instant
PostgreSQL: timestamptz
```

Örnek:
- `created_at`
- `updated_at`
- `joined_at`
- `added_at`
- `assigned_at`
- `due_at`
- `completed_at`
- `expires_at`
- `used_at`
- `revoked_at`

Application mental modeli UTC instant'tır.

### Enum persistence

Application enum'ları readable string value olarak persist edilir. Ordinal enum persistence kullanılmaz.

DB tarafında uygun CHECK constraint ile allowed values korunur.

### Constraint naming

```text
pk_<table>
uq_<table>_<meaning>
fk_<table>_<meaning>
ck_<table>_<meaning>
```

Expression/partial unique index gerekiyorsa anlamlı isim verilir.

## 7. Database Schema Contract

Bu bölüm **target schema'yı** tanımlar.

Bu doküman SQL migration çözümünü vermez.

Öğrenci Flyway migration'larını kendisi yazar. Spec yalnız gerekli field, type, nullability, key, constraint, relationship ve lifecycle bilgisini verir.

### 7.1 `users`

Kimlik (parola, login, token) Keycloak'tadır. `users` tablosu yerel profil ve domain ilişkileri içindir.

| Field | PostgreSQL type | Null | Not |
|---|---|---:|---|
| `id` | `uuid` | no | PK, UUID v7 (uygulama üretir; Keycloak id'si değil) |
| `auth_subject` | `varchar(255)` | no | token'daki `sub` (Keycloak user id); UNIQUE |
| `email` | `varchar(320)` | no | normalized lowercase; token claim'inden senkronize |
| `display_name` | `varchar(100)` | no | user-visible name |
| `status` | `varchar` | no | `ACTIVE`, `DEACTIVATED` |
| `created_at` | `timestamptz` | no | |
| `updated_at` | `timestamptz` | no | |

Constraints:
- PK: `id`
- `auth_subject` UNIQUE
- `email` globally UNIQUE
- persisted `email` lowercase-normalized olmalıdır
- `display_name` blank olamaz
- `status` yalnız `ACTIVE` / `DEACTIVATED`
- parola hiçbir biçimde uygulama DB'sinde tutulmaz

Provisioning:
- Kullanıcı Keycloak'ta kayıt olur; uygulamaya ilk authenticated isteğinde yerel satırı oluşturulur (just-in-time)
- Sonraki isteklerde `email` claim'i değişmişse yerel kopya güncellenir
- Yerel `id` ile Keycloak `sub`'ı ayrı tutulur: domain kimlik sağlayıcıya bağlanmaz, sağlayıcı değişirse yalnız eşleme değişir

Lifecycle:
- User hard-delete edilmez
- Account deactivation `status = DEACTIVATED` + Keycloak'ta kullanıcının disable edilmesi ve session'larının kapatılması
- DEACTIVATED kullanıcının elinde hâlâ geçerli bir access token olsa bile istekleri reddedilir (eşleme adımında status kontrolü)

Relationships:
- User → WorkspaceMember: 1:N
- User → TodoAssignee: 1:N

`created_by_user_id`, `added_by_user_id`, `assigned_by_user_id` gibi audit amaçlı alanlar JPA object graph'ını gereksiz büyütmemek için scalar UUID olarak tutulabilir; DB FK yine `users.id`'ye gider.

### 7.2 `workspaces`

| Field | Type | Null | Not |
|---|---|---:|---|
| `id` | `uuid` | no | PK |
| `name` | `varchar(120)` | no | |
| `status` | `varchar` | no | `ACTIVE`, `ARCHIVED` |
| `created_by_user_id` | `uuid` | no | FK → users |
| `created_at` | `timestamptz` | no | |
| `updated_at` | `timestamptz` | no | |

Constraints:
- PK: `id`
- `name` blank olamaz
- workspace name global unique değildir
- `status` allowed-value CHECK
- `created_by_user_id` → `users.id`

Lifecycle:
- hard delete yok
- archive edilir
- archived workspace üzerinde yeni project/todo/tag/member/assignment oluşturulmaz

Creation invariant:

```text
Workspace
+
OWNER WorkspaceMember
```

aynı transaction içinde oluşturulur.

### 7.3 `workspace_members`

User ↔ Workspace relationship'i açık entity'dir.

| Field | Type | Null | Not |
|---|---|---:|---|
| `id` | `uuid` | no | PK |
| `workspace_id` | `uuid` | no | FK |
| `user_id` | `uuid` | no | FK |
| `role` | `varchar` | no | `OWNER`, `MEMBER` |
| `joined_at` | `timestamptz` | no | |
| `added_by_user_id` | `uuid` | yes | FK → users |
| `updated_at` | `timestamptz` | no | |
| `version` | `bigint` | no | optimistic locking |

Constraints:
- PK: `id`
- UNIQUE `(workspace_id, user_id)`
- `workspace_id` → `workspaces.id`
- `user_id` → `users.id`
- `added_by_user_id` → `users.id` when present
- role CHECK: OWNER/MEMBER

Business invariants:
- aynı user aynı workspace'te yalnız bir membership
- workspace bir veya daha fazla OWNER taşıyabilir
- workspace hiçbir zaman 0 OWNER'a düşemez
- son OWNER ayrılamaz
- son OWNER MEMBER'a düşürülemez
- yalnız OWNER membership/role yönetir

Concurrency:
- `version` gerçek concurrent role/member update senaryoları için kullanılır
- optimistic locking tek başına last-owner invariant'ını çözmez; transaction/query locking tasarımı gerekir

### 7.4 `projects`

| Field | Type | Null |
|---|---|---:|
| `id` | `uuid` | no |
| `workspace_id` | `uuid` | no |
| `name` | `varchar(120)` | no |
| `description` | `varchar(1000)` | yes |
| `status` | `varchar` | no |
| `created_by_user_id` | `uuid` | no |
| `created_at` | `timestamptz` | no |
| `updated_at` | `timestamptz` | no |

Constraints:
- PK `id`
- FK `workspace_id` → workspaces
- FK `created_by_user_id` → users
- status: `ACTIVE`, `ARCHIVED`
- name blank olamaz
- workspace içinde project name **case-insensitive unique**
- composite candidate key UNIQUE `(id, workspace_id)`; same-workspace Todo FK'si bunu referans alır

Lifecycle:
- hard delete yok
- archive
- archived project'e yeni Todo eklenmez
- project başka workspace'e taşınmaz

### 7.5 `todos`

| Field | Type | Null |
|---|---|---:|
| `id` | `uuid` | no |
| `workspace_id` | `uuid` | no |
| `project_id` | `uuid` | yes |
| `title` | `varchar(200)` | no |
| `description` | `varchar(2000)` | yes |
| `status` | `varchar` | no |
| `priority` | `varchar` | no |
| `due_at` | `timestamptz` | yes |
| `completed_at` | `timestamptz` | yes |
| `created_by_user_id` | `uuid` | no |
| `created_at` | `timestamptz` | no |
| `updated_at` | `timestamptz` | no |
| `version` | `bigint` | no |

`TodoStatus`:
```text
TODO
IN_PROGRESS
DONE
CANCELLED
```

`TodoPriority`:
```text
LOW
MEDIUM
HIGH
URGENT
```

Constraints:
- PK `id`
- title blank olamaz
- workspace FK
- creator FK
- status CHECK
- priority CHECK
- `completed_at` yalnız DONE state ile tutarlı olmalıdır
- composite candidate key UNIQUE `(id, workspace_id)`
- optional Project relationship aynı workspace'te olmalıdır: `(project_id, workspace_id)` → `projects(id, workspace_id)`

State behavior:
- new Todo default: `TODO`, `MEDIUM`
- DONE olduğunda completedAt set edilir
- DONE'dan yeniden açılırsa completedAt temizlenir
- CANCELLED, DELETE değildir
- due date geçmiş olabilir
- Todo workspace değiştirmez

Concurrency:
- `version` optimistic locking için tutulur
- concurrent update/status changes üzerinde kullanılır

Delete:
- Todo için gerçek hard-delete endpoint vardır
- delete use-case aynı transaction içinde TodoTag ve TodoAssignee rows'larını kontrollü kaldırır, sonra Todo'yu siler
- DB tarafında sessiz geniş cascade yerine integrity görünür tutulur

### 7.6 `tags`

| Field | Type | Null |
|---|---|---:|
| `id` | `uuid` | no |
| `workspace_id` | `uuid` | no |
| `name` | `varchar(50)` | no |
| `color` | `varchar(7)` | yes |
| `created_by_user_id` | `uuid` | no |
| `created_at` | `timestamptz` | no |
| `updated_at` | `timestamptz` | no |

Constraints:
- PK
- workspace FK
- creator FK
- name blank olamaz
- workspace içinde name case-insensitive unique
- color varsa `#RRGGBB` formatını karşılamalı
- composite candidate key UNIQUE `(id, workspace_id)`

Delete:
- Tag hard-delete edilebilir
- önce ilgili `todo_tags` ilişkileri aynı transaction'da kaldırılır
- Todo kayıtları silinmez

### 7.7 `todo_tags`

Todo ↔ Tag N:N ilişkisinin explicit join entity'sidir.

| Field | Type | Null |
|---|---|---:|
| `id` | `uuid` | no |
| `workspace_id` | `uuid` | no |
| `todo_id` | `uuid` | no |
| `tag_id` | `uuid` | no |
| `added_by_user_id` | `uuid` | no |
| `added_at` | `timestamptz` | no |

Constraints:
- PK
- UNIQUE `(todo_id, tag_id)`
- same-workspace composite FK:
  - `(todo_id, workspace_id)` → todos
  - `(tag_id, workspace_id)` → tags
- `added_by_user_id` → users

JPA relationship direction:
- TodoTag → Todo: ManyToOne LAZY
- TodoTag → Tag: ManyToOne LAZY
- Todo → TodoTag collection: OneToMany only when domain navigation requires it
- Tag inverse collection is optional

`@ManyToMany` kullanılmaz.

Lifecycle:
- relationship removal Todo veya Tag'i silmez
- Todo delete veya Tag delete use-case'inde join rows kontrollü temizlenir

### 7.8 `todo_assignees`

Todo ↔ User multi-assignment relationship'idir.

| Field | Type | Null |
|---|---|---:|
| `id` | `uuid` | no |
| `workspace_id` | `uuid` | no |
| `todo_id` | `uuid` | no |
| `assignee_user_id` | `uuid` | no |
| `assigned_by_user_id` | `uuid` | no |
| `assigned_at` | `timestamptz` | no |

Constraints:
- PK
- UNIQUE `(todo_id, assignee_user_id)`
- `(todo_id, workspace_id)` → todos same-workspace composite FK
- `(workspace_id, assignee_user_id)` → workspace_members `(workspace_id, user_id)`
- `assigned_by_user_id` → users

Meaning:
- DB, assignee'nin gerçekten Todo workspace'inin member'ı olmasını enforce eder
- assignment'ı yapan kullanıcının yetkisi application authorization'da kontrol edilir

Membership delete consequence:
- WorkspaceMember silinmeden önce o membership'e bağlı TodoAssignee kayıtları bilinçli olarak çözülmelidir
- DB FK bu unutulursa delete'i engeller
- sessiz `ON DELETE CASCADE` varsayılan değildir

### 7.9 `refresh_tokens` — kaldırıldı (2026-10-24)

Refresh token'ları Keycloak yönetir. V3 ile oluşturulan tablo, Keycloak eşleme adımının (ROADMAP K3) migration'ında drop edilir.

## 8. Relationship summary

```text
User
  1 ───── N WorkspaceMember N ───── 1 Workspace
                                         │
                                         ├── 1:N Project
                                         ├── 1:N Todo
                                         └── 1:N Tag

Project 1 ───── N Todo

Todo 1 ───── N TodoTag N ───── 1 Tag

Todo 1 ───── N TodoAssignee N ───── 1 User
```

Workspace bütün workspace-scoped sorguların isolation boundary'sidir.

## 9. Authorization model

Roles:
```text
OWNER
MEMBER
```

OWNER:
- workspace rename/archive
- membership list/add/remove
- role promote/demote
- project/todo/tag operations
- assignment operations
- bütün workspace content'e yönetim erişimi

MEMBER:
- workspace görüntüleme
- project/todo/tag görüntüleme
- project create/update
- todo create/update
- tag create/update
- kendi yetkisi olan resource davranışları

Resource rules:
- OWNER her Todo'yu yönetebilir
- Todo creator hard-delete edebilir
- Todo creator veya OWNER assignee yönetebilir
- assignee status progression gibi belirli Todo action'larını yapabilir
- bütün resource access workspace membership ile scope edilir

Exact permission matrix feature uygulanmadan önce küçük use-case bazında netleştirilir; genel permission framework'ü baştan yazılmaz.

Query-level scoping tercih edilir: resource lookup mümkün olduğunda `resource id + workspace id` ile yapılır.

## 10. Authentication / Security

**Karar (2026-10-24): kimlik yönetimi Keycloak'tadır.** Daha önce uygulamada yazılan register / login / JWT üretimi /
refresh token rotation / login rate limit kaldırılır. Gerekçe: production'da kimlik yönetimini kendin yazmak her açığı
kendin kapatmayı gerektirir; Keycloak bunları (MFA, sosyal login, brute force, rotation) hazır ve denetlenmiş sunar.
Öğrenme hedefi kimlik kodunu yazmaktan, OIDC'yi ve bir kimlik sağlayıcıyı işletmeyi anlamaya kayar.

```text
istemci ──(1) authorization code + PKCE──▶ Keycloak (login sayfası, kayıt, parola, MFA, brute force)
istemci ◀─(2) access token + refresh token──
istemci ──(3) Authorization: Bearer <access token>──▶ DeepToDo API (Resource Server)
                                                       │ imzayı Keycloak'ın JWKS'i ile doğrular (iss, exp, aud)
                                                       │ sub → yerel users satırı (yoksa oluşturur)
                                                       ▼ workspace yetkisi DB'den (§9)
```

### Keycloak
- Tek realm: `deeptodo`. Realm yapılandırması export dosyası olarak repoda versiyonlanır; local'de açılışta import edilir
- Keycloak kendi database'ini kullanır (aynı PostgreSQL sunucusunda ayrı database); uygulama o database'e dokunmaz
- Client'lar: API'nin audience'ı (`deeptodo-api`); kullanıcı tarafı public client (PKCE); Admin API için service account client.
  Local'e özel test client'ı direct access grant'e (curl ile token almak için) izin verebilir; production'da kapalı
- Kayıt: Keycloak'ın self-registration'ı. Register otomatik workspace oluşturmaz
- Brute force detection, password policy, token ömürleri, refresh token rotation / reuse: realm ayarı

### Resource Server
- Spring Security'nin yerleşik JWT desteği: `issuer-uri` ile discovery, JWKS ile imza doğrulaması, `exp` / `iss` / `aud` kontrolü
- Özel JWT filtresi yazılmaz
- Dinamik workspace üyeliği / yetkisi JWT içine gömülmez; Keycloak rolleri workspace yetkisi için kullanılmaz (§9)
- Session stateless; CSRF kapalı (cookie ile kimlik taşınmıyor, yalnız `Authorization` header'ı)

### Yerel kullanıcı
- `sub` → `users.auth_subject` eşlemesi her authenticated istekte yapılır (§7.1)
- Domain kodu yerel user id'yi kullanır; Keycloak id'si `auth` paketinin dışına çıkmaz

### Workspace creation
Authenticated user workspace oluşturduğunda Workspace + OWNER WorkspaceMember aynı transaction'da oluşur.

### Hesap kapatma
- Yerel `status = DEACTIVATED` + Keycloak Admin API ile disable ve logout
- Admin API çağrısı DB transaction'ının dışında yapılır. İki sistem arasında atomiklik yoktur (dual-write);
  bu önce bilinçli olarak kabul edilir, Kafka öğrenme modülünde (§2.1) transactional outbox ile çözülür

## 11. API surface — V1 target

Exact payload DTO'ları ilgili feature task'ında tasarlanır.

Bütün application endpoint'leri `/api` öneki altındadır (Actuator hariç).

Auth: uygulamada auth endpoint'i yoktur. Kayıt, login, refresh ve logout Keycloak'ın OIDC endpoint'leridir
(`/realms/deeptodo/protocol/openid-connect/...`).

Current user:
```text
GET    /api/users/me
PATCH  /api/users/me
DELETE /api/users/me
```

`PATCH /users/me` display name gibi düşük-risk profil alanlarıyla sınırlıdır. `DELETE` physical delete değil account deactivation'dır.

Workspaces:
```text
POST   /api/workspaces
GET    /api/workspaces
GET    /api/workspaces/{workspaceId}
PATCH  /api/workspaces/{workspaceId}
POST   /api/workspaces/{workspaceId}/archive
```

Members:
```text
GET    /api/workspaces/{workspaceId}/members
POST   /api/workspaces/{workspaceId}/members
PATCH  /api/workspaces/{workspaceId}/members/{memberId}
DELETE /api/workspaces/{workspaceId}/members/{memberId}
```

Invitation sistemi yok. OWNER yalnız sistemde zaten mevcut User'ı ekler.

Projects:
```text
POST   /api/workspaces/{workspaceId}/projects
GET    /api/workspaces/{workspaceId}/projects
GET    /api/workspaces/{workspaceId}/projects/{projectId}
PATCH  /api/workspaces/{workspaceId}/projects/{projectId}
POST   /api/workspaces/{workspaceId}/projects/{projectId}/archive
```

Todos:
```text
POST   /api/workspaces/{workspaceId}/todos
GET    /api/workspaces/{workspaceId}/todos
GET    /api/workspaces/{workspaceId}/todos/{todoId}
PATCH  /api/workspaces/{workspaceId}/todos/{todoId}
DELETE /api/workspaces/{workspaceId}/todos/{todoId}
```

Tags:
```text
POST   /api/workspaces/{workspaceId}/tags
GET    /api/workspaces/{workspaceId}/tags
PATCH  /api/workspaces/{workspaceId}/tags/{tagId}
DELETE /api/workspaces/{workspaceId}/tags/{tagId}
```

Todo tags:
```text
PUT    /api/workspaces/{workspaceId}/todos/{todoId}/tags/{tagId}
DELETE /api/workspaces/{workspaceId}/todos/{todoId}/tags/{tagId}
```

Todo assignees:
```text
PUT    /api/workspaces/{workspaceId}/todos/{todoId}/assignees/{userId}
DELETE /api/workspaces/{workspaceId}/todos/{todoId}/assignees/{userId}
```

## 12. Todo list/query scope

Todo list endpoint şu alanları destekleyebilir:
- `status`
- `priority`
- `projectId`
- `tagId`
- `assigneeUserId`
- due-date range
- simple title/description search
- pagination
- sorting

V1 search: case-insensitive contains/pattern search. Full-text search engine yok.

Index policy:
- PK/UNIQUE index'leri doğal olarak oluşur
- FK-side index ihtiyaçları query workload ile değerlendirilir
- list query'leri oluşunca `EXPLAIN ANALYZE` ile karar verilir
- expression unique index gibi business invariant index'leri schema contract'ın parçasıdır

## 13. Validation / error handling

Katmanlar:
```text
request format
→ Bean Validation
→ business rule
→ DB constraint
→ concurrency invariant
```

API error contract en az:
- validation error
- not found
- conflict
- unauthorized
- forbidden
- unexpected internal error

Domain/business exception'lar HTTP dependency taşımamalıdır.

## 14. Delete / lifecycle policy

```text
User             → deactivate
Workspace        → archive
Project          → archive
Todo             → hard delete
Tag              → hard delete
WorkspaceMember  → hard delete
TodoTag          → hard delete
TodoAssignee     → hard delete
```

Soft-delete her tabloya mekanik uygulanmaz.

## 15. Testing policy

Automated testing ana geliştirme fazlarının sonuna bırakılır.

Concurrency'ye dayanan invariant'lar (aşağıdaki istisna) ve Redis / Kafka entegrasyonları, ilgili PR'da Testcontainers ile gerçek altyapıya karşı test edilir.

Phase 0–3 boyunca:
- code compile edilir
- application/manual smoke doğrulanır
- migration ve endpoint davranışı elle gözlenebilir
- Claude Code kullanıcı istemedikçe kapsamlı test suite eklemez

Phase 4'te risk bazlı test suite kurulur.

**İstisna — yarış durumuna dayanan invariant'lar:** son OWNER invariant'ı, uygulandığı task'ta otomatik bir concurrency testiyle doğrulanır (refresh rotation Keycloak'a geçtiği için bu listeden çıktı). Gerekçe: bu hatalar yalnız iki istek neredeyse aynı anda geldiğinde ortaya çıkar ve elle tekrar üretilemez. Test Phase 4'e bırakılırsa locking tasarımının doğru olup olmadığı iki faz boyunca bilinmez. İstisna yalnız bu invariant içindir; genel test politikası değişmez.

Test scopes:
- Unit: status transition, owner invariant helper/business branch, authorization branching
- MVC: request binding, validation, response/error contract
- Persistence/PostgreSQL Testcontainers: mapping, FK, UNIQUE, CHECK, composite FK, query behavior, optimistic locking
- Security: 401/403, token doğrulama (issuer / audience / süre), workspace isolation, ownership/assignee rules; seçili bir akış Testcontainers Keycloak ile
- Integration: selected critical flows only

Her class için test yazılmaz.

## 16. Production polish — V1

V1 sonunda:
- structured logging
- correlation/request ID
- sensitive-data redaction
- environment-based config
- Actuator health
- basic JVM/application metrics
- graceful shutdown
- production Dockerfile
- `.dockerignore`
- container non-root runtime
- production-like Docker Compose/runtime configuration
- public OpenAPI UI/docs
- public HTTPS deployment

Kapsam dışında:
- custom dashboards
- distributed tracing
- alerting platform
- Kubernetes
- advanced CI/CD platform

## 17. Public deployment learning goal

Proje yalnız localhost'ta bitmez.

Final hedef:
```text
source
→ Maven build
→ Docker image
→ runtime configuration
→ deployed container
→ HTTPS
→ public API URL
```

Deployment constraints:
- maddi kazanç hedefi yok
- free/low-cost learning environment tercih edilir
- ücretsiz provider subdomain kabul edilir
- custom paid domain zorunlu değildir
- Kubernetes kullanılmaz
- provider-neutral yaklaşım korunur

Küçük VPS/free VM kullanılırsa:
```text
SSH
→ Docker
→ Compose
→ reverse proxy
→ HTTPS
→ Spring Boot + Keycloak
→ PostgreSQL
```

Keycloak production modunda çalışır (`hostname`, reverse proxy header'ları, admin console'un public olmaması).

PaaS kullanılırsa aynı artifact/runtime/config mental modeli korunur.

Public:
- application API
- OpenAPI docs/UI
- minimum güvenli health endpoint

Actuator'ın hassas operational endpoint'leri public edilmez.

## 18. Development phases

Toplam 5 phase.

**Sıra kararı (2026-10-10):** Phase 3'ün authentication kısmı (password field, register, login, JWT) Phase 1'den önce ele alınır; Onat'ın Security öğrenme akışını takip eder. Resource/workspace authorization adımları ilgili feature'lar (Workspace, Todo) var olduktan sonra uygulanır.

### Phase 0 — Bootstrap
- Spring Boot project
- PostgreSQL local Docker Compose
- Flyway
- configuration
- application boots
- manual smoke
- business scope yok

### Phase 1 — Todo Core
- `users` minimal target fields, security password field hariç
- `workspaces`
- fixed dev user/workspace seed
- `todos`
- entity/repository/service/controller
- request/response DTO
- validation/error contract
- CRUD
- status transition
- optimistic `version`
- hard delete
- workspace-scoped queries
- automated tests henüz zorunlu değil

### Phase 2 — Workspace / Collaboration / Querying
Vertical slices:
1. Workspace API + create-owner transaction
2. WorkspaceMember + OWNER/MEMBER
3. last-owner invariant (+ concurrency testi, bkz. §15 istisnası)
4. Project
5. Todo optional Project relationship
6. Tag
7. TodoTag
8. TodoAssignee
9. pagination/sorting/filtering
10. N+1/fetch plan
11. index review + EXPLAIN ANALYZE
12. selected concurrency behavior

### Phase 3 — Security

Karar (2026-10-24): authentication Keycloak'a devredildi. Önceden yazılan kendi auth kodu (password, register, login,
JWT üretimi, refresh token, rate limit) kaldırılır.

1. Keycloak local kurulum + realm export
2. Resource Server: issuer, JWKS, audience
3. Yerel kullanıcı eşleme (just-in-time provisioning)
4. Hesap: me endpoint'leri + deactivation (Keycloak Admin API)
5. Keycloak güvenlik ayarları (brute force, password policy, token ömürleri, rotation)
6. Workspace / resource authorization (her feature ile)
7. CSRF / CORS / session-policy kararları

### Phase 4 — Testing + Production Polish + Deployment

A. Testing:
- unit
- MVC
- persistence/Testcontainers
- security
- selected integration/concurrency

B. Production polish:
- structured logging
- correlation ID
- health
- metrics basics
- config hardening
- graceful shutdown
- OpenAPI

C. Docker/deploy:
- Maven production build
- Dockerfile
- non-root image
- Docker Compose/runtime
- public host
- HTTPS
- OpenAPI verification
- health verification
- redeploy new version

V1 public deploy ile kapanır.

### Phase 5 — Redis / Kafka öğrenme modülleri

§2.1'deki use-case'ler, her biri ayrı küçük PR olarak. Sıra ve Phase 1–4'e göre zamanlaması Onat'ın kararıdır ve STATUS'a yazılır.
Önerilen sıra: Redis kullanıcı eşleme cache'i → Redis API rate limit → outbox tablosu + relay → `UserDeactivated` → (Todo'dan sonra) `WorkspaceMemberRemoved`.

## 19. Task sizing

Bir task tek coherent vertical slice olmalıdır.

İyi:
```text
DT-014 Create Todo
```

Gerekirse migration + entity + repository + service + controller aynı task'a girebilir.

Kötü:
```text
Implement Todo module
```

Mekanik küçük işler formal task olmak zorunda değildir.

**Bir task = bir PR.** PR boyutu ve şablonu `CLAUDE.md`'dedir (yaklaşık 300 satır / 10 dosya sınırı, tek kavram).
Bir dikey dilim bu sınırı aşıyorsa sıralı birkaç PR'a bölünür (ör. migration + entity → service → endpoint).

## 20. Definition of Done

Bir feature tamamlandığında, mevcut phase izin verdiği ölçüde:
- scope anlaşılmış
- API contract net
- schema contract ile migration uyumlu
- JPA mapping doğru
- transaction boundary bilinçli
- authorization boundary tanımlıysa uygulanmış
- critical invalid state DB/application tarafından korunuyor
- manual/runtime behavior doğrulanmış
- Phase 4'te ilgili risk için test eklenmiş
- gereksiz abstraction/dependency eklenmemiş

## 21. Scope-control rules

- Redis/Kafka yalnız §2.1'deki use-case'lerde kullanılır; "production gibi dursun" diye başka yere eklenmez
- Kimlik Keycloak'tadır; uygulamaya yeniden parola / token / refresh kodu eklenmez
- microservice'e bölünmez
- generic framework yazılmaz
- design pattern adı göstermek için pattern eklenmez
- bütün JPA relationship çeşitleri sırf öğrenildi diye projeye zorla konmaz
- her entity'ye `@Version` mekanik eklenmez
- her entity soft-delete edilmez
- her business check yalnız Java pre-check ile bırakılmaz; DB invariant mümkünse kullanılır
- yeni teknoloji ancak somut problem/learning objective ile eklenir

`PROJECT_SPEC.md` hedefi gösterir.

`STATUS.md` mevcut gerçeği gösterir.

Kod ile doküman çelişirse çelişki görünür hale getirilir.
