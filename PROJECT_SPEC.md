# DeepToDo — Project Specification v5

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
- Spring Security
- authentication / authorization
- backend testing
- logging / health / metrics
- Docker build ve public deployment
- Redis (paylaşılan state, TTL'li veri) ve Kafka (domain event'leri, transactional outbox) — §2.1 öğrenme modülleri

Proje agentic geliştirilir: kodu Claude küçük, öğretici PR'larla yazar ve `develop`'a merge eder; Onat yönlendirir, okur ve sorar (bkz. `CLAUDE.md`).

Proje **scope kontrollü** tutulur. Kursta öğrenilen her teknolojiyi DeepToDo'ya eklemek hedef değildir.

Ana ilke:

> Kurslar breadth öğretir; DeepToDo yalnız ürünün gerçekten ihtiyaç duyduğu konuları kaliteli şekilde uygular.

## 2. V1 ürün sınırı

### Dahil

- User registration / login
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
- JWT access token
- Opaque rotating refresh token
- Refresh-token reuse detection
- Login brute-force koruması (in-memory rate limit)
- Resource-based authorization
- Public OpenAPI documentation
- Final aşamada Docker image + public HTTPS deployment

### Kapsam dışında

- PERSONAL / TEAM workspace ayrımı
- `ADMIN` role
- automatic personal workspace
- invitation/email workflow
- email verification
- forgot-password
- MFA
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

### 2.1 Öğrenme modülleri — Redis ve Kafka (karar 2026-10-23)

Redis ve Kafka, uygulama tek instance çalıştığı sürece **zorunlu değildir**; öğrenme amacıyla, kodda zaten var olan
somut bir problemi çözen yerlere eklenir. Her use-case ayrı, küçük bir PR'dır. Listede olmayan bir yere eklenmez.

Redis:
- **Paylaşılan login rate limit:** in-memory `LoginRateLimiter` yerine Redis sayaçları (`INCR` + `EXPIRE`); birden çok instance aynı sayacı görür.
- **Access token denylist:** logout ve hesap kapatmada token'ın `jti`'si, kalan ömrü kadar TTL ile Redis'e yazılır; JWT doğrulaması bu listeye de bakar. Kapatılan hesabın access token'ının 15 dk daha geçerli kalması sorununu kapatır.
- **(Opsiyonel) Üyelik cache'i:** `requireMember` sonucunun cache'lenmesi ve rol/üyelik değişince invalidation. Yetki verisini cache'lemenin riskleri (bayat yetki) bu use-case'in öğrenme konusudur.

Kafka:
- **Transactional outbox:** domain event'leri iş verisiyle aynı transaction'da `outbox_events` tablosuna yazılır; ayrı bir relay bunları Kafka'ya yayınlar (dual-write problemi yok).
- **`UserDeactivated` event'i:** `auth` modülü dinler ve kullanıcının refresh token'larını revoke eder (`user` → `auth` doğrudan bağımlılığı kalkar).
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
├── auth            # authentication'ın tamamı: SecurityFilterChain, UserDetailsService, token üretimi, refresh token
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

| Field | PostgreSQL type | Null | Not |
|---|---|---:|---|
| `id` | `uuid` | no | PK, UUID v7 |
| `email` | `varchar(320)` | no | normalized lowercase |
| `display_name` | `varchar(100)` | no | user-visible name |
| `password_hash` | `varchar(255)` | no* | Security phase'inde eklenir |
| `status` | `varchar` | no | `ACTIVE`, `DEACTIVATED` |
| `created_at` | `timestamptz` | no | |
| `updated_at` | `timestamptz` | no | |

`password_hash`, Security phase öncesi schema'da bulunmak zorunda değildir; eklendiği migration'dan sonra target modelde NOT NULL olur.

Constraints:
- PK: `id`
- `email` globally UNIQUE
- persisted `email` lowercase-normalized olmalıdır
- `display_name` blank olamaz
- `status` yalnız `ACTIVE` / `DEACTIVATED`
- plaintext password hiçbir zaman persist edilmez

Lifecycle:
- User hard-delete edilmez
- Account deactivation `status = DEACTIVATED`
- deactivated user login/refresh yapamaz
- account deactivation aktif refresh-token family'lerini revoke eder

Relationships:
- User → WorkspaceMember: 1:N
- User → TodoAssignee: 1:N
- User → RefreshToken: 1:N

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

### 7.9 `refresh_tokens`

Security phase'inde eklenir. Her rotation yeni row üretir.

| Field | Type | Null |
|---|---|---:|
| `id` | `uuid` | no |
| `family_id` | `uuid` | no |
| `user_id` | `uuid` | no |
| `token_hash` | `varchar(64)` | no |
| `expires_at` | `timestamptz` | no |
| `used_at` | `timestamptz` | yes |
| `revoked_at` | `timestamptz` | yes |
| `created_at` | `timestamptz` | no |

Constraints:
- PK
- user FK
- token_hash UNIQUE
- expiry created_at'tan sonra olmalı

Security behavior:
```text
random opaque refresh token
→ SHA-256
→ token_hash persist
```

Refresh:
```text
valid unused token
→ mark used
→ create successor in same family
→ return successor
```

Used token tekrar sunulursa:
```text
reuse detected
→ family compromised
→ active tokens in family revoke
```

- raw refresh token DB'de tutulmaz
- logout current family/session'ı revoke eder
- account deactivation user'ın aktif refresh token'larını revoke eder
- mekanik `@Version` zorunlu değildir; correctness transaction + conditional update/query ile tasarlanır
- token'lar arası halef bağlantısı (`replaced_by_token_id`) tutulmaz: katı reuse detection için `used_at` + `family_id` yeterli (karar 2026-10-15, §10). Grace window'a geçilirse bu karar yeniden açılır.

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

User 1 ───── N RefreshToken
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

**Karar (2026-10-25): authentication uygulamada, Spring Security ile yazılır.** 2026-10-24'te kimlik Keycloak'a
devredilmiş (spec v4), local kurulum ve resource server doğrulaması yapılmıştı (ROADMAP K1–K2). Onat'ın kararıyla geri
dönüldü: projenin öğrenme hedefi Spring Security'nin içi (filter chain, `AuthenticationManager`, JWT imzalama, refresh
rotation); Keycloak bunları kara kutuya koyuyor. Keycloak denemesinden kalan kavramlar (`kid`, `iss` / `aud` doğrulaması,
key rotation) kendi token'larımızda uygulanır.

Auth kodu sıfırdan, küçük adımlarla yeniden kurulur (ROADMAP R). Önceki implementasyon (`679ece5`) yalnız referanstır.

### Register
- yalnız User oluşturur
- automatic workspace oluşturmaz

### Workspace creation
Authenticated user workspace oluşturduğunda Workspace + OWNER WorkspaceMember aynı transaction'da oluşur.

### Login
- normalized email
- password hash verification
- ACTIVE user requirement
- brute-force koruması: login denemeleri rate limit ile sınırlanır

Gerekçe: uygulama public HTTPS üzerinden internete açılır; sınırsız deneme kabul eden bir login endpoint'i parola tahmini saldırısına açıktır. İlk uygulama in-memory'dir (tek instance). Redis öğrenme modülünde (§2.1) sayaçlar Redis'e taşınır; böylece birden çok instance aynı sayacı paylaşır.

### Access token
- short-lived JWT, RS256 (asimetrik: private key yalnız imzalar, public key doğrular)
- header'da `kid`: key rotation sırasında hangi key ile doğrulanacağı token'dan bilinir
- claim'ler: `iss`, `sub` (yerel user id), `aud` (API'nin adı), `iat`, `exp`, `jti`
- doğrulamada `iss`, `aud`, `exp` kontrol edilir
- key'ler repoda değil; local'de `secrets/` (gitignore), production'da env / secret
- response body ile client'a döner
- API request'te `Authorization: Bearer`
- dynamic workspace membership/permission state JWT içine gömülmez
- modern Spring Security built-in JWT Resource Server support tercih edilir
- custom JWT validation filter varsayılan çözüm değildir

### Refresh token
- opaque random token
- HttpOnly cookie
- Secure in production
- uygun SameSite policy
- DB'de yalnız hash
- rotation
- family
- reuse detection
- revoke/logout

Cookie kullanan refresh/logout endpoint'lerinde CSRF kararı bilinçli ele alınır.

**Karar (2026-10-15) — eşzamanlı refresh: katı reuse detection, grace window yok.** Kullanıcı uygulamayı iki sekmede açıkken iki sekme aynı refresh token'ı neredeyse aynı anda kullanabilir; ikinci istek "kullanılmış token" olarak görülür, aile revoke edilir ve kullanıcı yeniden login olur. Bu yanlış pozitif, basitlik ve güvenlik karşılığında bilinçli olarak kabul edildi.

Refresh token cookie'si: `HttpOnly`, `SameSite=Strict`, `Path=/api/auth`, production'da `Secure`. CSRF koruması kapalı kalır; cookie'li `/refresh` ve `/logout` cross-site isteklere karşı `SameSite=Strict` ile korunur.

## 11. API surface — V1 target

Exact payload DTO'ları ilgili feature task'ında tasarlanır.

Bütün application endpoint'leri `/api` öneki altındadır (Actuator hariç).

Auth:
```text
POST   /api/auth/register
POST   /api/auth/login
POST   /api/auth/refresh
POST   /api/auth/logout
```

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
RefreshToken     → revoke/expire; cleanup later
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

**İstisna — yarış durumuna dayanan invariant'lar:** son OWNER invariant'ı ve refresh token rotation/reuse detection, uygulandıkları task'ta birer otomatik concurrency testiyle doğrulanır. Gerekçe: bu hatalar yalnız iki istek neredeyse aynı anda geldiğinde ortaya çıkar ve elle tekrar üretilemez. Test Phase 4'e bırakılırsa locking tasarımının doğru olup olmadığı iki faz boyunca bilinmez. İstisna yalnız bu iki invariant içindir; genel test politikası değişmez.

Test scopes:
- Unit: status transition, owner invariant helper/business branch, token rotation logic, authorization branching
- MVC: request binding, validation, response/error contract
- Persistence/PostgreSQL Testcontainers: mapping, FK, UNIQUE, CHECK, composite FK, query behavior, optimistic locking
- Security: login, refresh, 401/403, workspace isolation, ownership/assignee rules
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
→ Spring Boot
→ PostgreSQL
```

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

Karar (2026-10-25): Keycloak denemesinden sonra auth sıfırdan yeniden kurulur. Sıra (ROADMAP R):

1. Spring Security'nin varsayılan davranışı → kendi `SecurityFilterChain` (stateless, CSRF kararı, 401 ProblemDetail)
2. password field / PasswordEncoder + register
3. login/authentication pipeline (`AuthenticationManager`, `DaoAuthenticationProvider`, `UserDetailsService`)
4. JWT access token (RS256, `kid`)
5. Resource Server validation (`iss`, `aud`, `exp`) + current user
6. key rotation
7. refresh_tokens (V3 tablosu zaten var) + cookie
8. rotation (+ concurrency testi, bkz. §15 istisnası)
9. reuse detection (katı; grace window yok)
10. logout / hesap kapatma + token revoke
11. login brute-force koruması (in-memory rate limit)
12. workspace/resource authorization (her feature ile)

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
Önerilen sıra: Redis rate limit → access token denylist → outbox tablosu + relay → `UserDeactivated` → (Todo'dan sonra) `WorkspaceMemberRemoved`.

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
