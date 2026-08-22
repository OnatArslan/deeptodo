# Deeptodo — Project Architecture

> Bu doküman `deeptodo` projesinin ürün sınırlarını, domain modelini, mimari yaklaşımını ve gelişim yönünü tanımlar. Yaşayan bir dokümandır; proje ilerledikçe alınan önemli kararlarla güncellenir.

## 1. Proje Tanımı

`deeptodo`, kişisel ve ekip tabanlı çalışma alanlarında proje ve görev yönetimi sağlayan bir collaborative task management backend uygulamasıdır.

Projenin amacı yalnızca çalışan bir CRUD API üretmek değildir. Aynı domain üzerinde Java, Spring Boot, Spring Security, JPA/Hibernate, SQL/PostgreSQL, transaction yönetimi, test stratejileri ve production backend davranışlarını uygulayarak öğrenmektir.

Domain bilinçli olarak orta büyüklükte tutulur:

- Basit bir Todo CRUD örneğinden daha gerçekçidir.
- Authentication, authorization, ilişkisel modelleme ve concurrency gibi gerçek backend problemlerini içerir.
- Microservice, messaging ve dağıtık sistem karmaşıklığını ilk aşamada taşımaz.

## 2. Öğrenme Hedefleri

- Spring Boot request lifecycle ve dependency injection davranışını anlamak
- Controller, application/service ve persistence sorumluluklarını doğru ayırmak
- PostgreSQL üzerinde tutarlı schema ve constraint tasarlamak
- JPA entity ilişkilerini ve Hibernate davranışlarını bilinçli kullanmak
- Transaction boundary ve concurrency problemlerini yönetmek
- Spring Security ile authentication ve resource-based authorization kurmak
- Unit, slice, integration ve persistence testlerinin sınırlarını öğrenmek
- Validation, error handling, logging, metrics ve configuration yönetimini production bakışıyla ele almak
- Tasarım kararlarının doğruluk, güvenlik, performans ve bakım maliyetlerini değerlendirmek

## 3. Kapsam

### Temel yetenekler

- Kullanıcı hesabı ve authentication
- Kişisel ve takım workspace'leri
- Workspace üyeliği ve rol yönetimi
- Workspace davetleri
- Workspace içinde project yönetimi
- Project'e bağlı veya bağımsız todo yönetimi
- Todo status, priority ve due date yönetimi
- Workspace seviyesinde tag yönetimi
- Todo'lara birden fazla tag eklenmesi
- Todo'ların birden fazla workspace üyesine atanması
- Pagination, sorting, filtering ve arama
- Kritik işlemlerde audit kaydı
- Güvenli refresh session yönetimi

### Bilinçli olarak kapsam dışında

- Microservice mimarisi
- Kafka veya başka bir message broker
- Redis ve distributed cache
- Event sourcing ve CQRS
- Gerçek zamanlı collaboration, WebSocket veya SSE
- Dosya yükleme ve attachment yönetimi
- Sosyal ağ veya friendship sistemi
- Mobil/web frontend
- Kubernetes deployment ayrıntıları
- Gereksinim oluşmadan eklenen generic repository, base service veya enterprise abstraction'lar

Bu maddeler kalıcı yasak değildir. Ancak somut bir ürün veya teknik ihtiyaç oluşmadan projeye eklenmez.

## 4. Teknoloji ve Mimari Kararlar

### Temel teknoloji seti

- Java 21
- Spring Boot
- Maven
- PostgreSQL
- Spring Data JPA / Hibernate
- Flyway
- Spring Security
- Bean Validation
- Docker Compose
- JUnit, Mockito, MockMvc ve Testcontainers
- Spring Boot Actuator ve Micrometer

Kütüphane ve framework sürümleri proje oluşturulurken uyumluluk gözetilerek sabitlenir; yalnızca yeni olduğu için milestone, snapshot veya henüz kararlı olmayan sürüm seçilmez.

### Mimari stil

Uygulama bir **modular monolith** olarak geliştirilir. Kod organizasyonunda **package-by-feature** kullanılır.

Başlangıçta klasik ve anlaşılır akış korunur:

```text
HTTP Request
    → Controller
    → Application/Service
    → Repository
    → PostgreSQL
```

- Controller HTTP sözleşmesini yönetir.
- Application/service katmanı use-case, authorization ve transaction sınırını yönetir.
- Domain nesneleri kendi durum geçişlerine ait kuralları korur.
- Repository persistence erişimini soyutlar.
- Veritabanı yalnızca depolama alanı değildir; veri bütünlüğünün son savunma hattıdır.

Hexagonal Architecture veya katı Clean Architecture başlangıç şartı değildir. Mevcut yapı gerçek bir problem üretirse sınırlar yeniden değerlendirilir.

### Paket yapısı

Hedef paket yapısı aşağıdaki gibidir:

```text
com.onatarslan.deeptodo
├── DeeptodoApplication
├── common
│   ├── error
│   ├── validation
│   ├── security
│   └── config
├── identity
│   ├── web
│   ├── application
│   ├── domain
│   └── persistence
├── workspace
│   ├── web
│   ├── application
│   ├── domain
│   └── persistence
├── project
│   ├── web
│   ├── application
│   ├── domain
│   └── persistence
├── todo
│   ├── web
│   ├── application
│   ├── domain
│   └── persistence
├── tag
│   ├── web
│   ├── application
│   ├── domain
│   └── persistence
└── audit
    ├── application
    ├── domain
    └── persistence
```

Alt paketler boş şablon olarak topluca oluşturulmaz. İlgili feature ve katman gerçekten oluştuğunda eklenir. `common`, feature'a ait kodların atıldığı genel bir çöp paketine dönüştürülmez.

## 5. Domain Modeli

### 5.1 User

| Field | Type | Açıklama |
|---|---|---|
| `id` | UUID | Kullanıcının sistem kimliği |
| `email` | String (max 320) | Login ve iletişim adresi |
| `displayName` | String (max 100) | Görünen ad |
| `passwordHash` | String | Güvenli parola özeti; security aşamasında eklenir |
| `status` | UserStatus | Hesap durumu |
| `createdAt` | Instant | Oluşturulma zamanı |
| `updatedAt` | Instant | Son güncellenme zamanı |
| `version` | Long | Optimistic concurrency kontrolü |

`UserStatus`: `ACTIVE`, `SUSPENDED`, `DEACTIVATED`

Kurallar:

- Email normalize edilir ve sistem genelinde benzersizdir.
- `displayName` boş veya yalnızca whitespace olamaz.
- Plaintext password hiçbir zaman entity veya kalıcı depoda tutulmaz.
- `SUSPENDED` ve `DEACTIVATED` kullanıcılar login veya session refresh yapamaz.
- Kullanıcı fiziksel olarak silinmek yerine deaktive edilir; geçmiş sahiplik ve audit bilgileri korunur.
- Yeni kullanıcı oluşturulurken personal workspace'i ve owner üyeliği aynı transaction içinde oluşturulur.

### 5.2 Workspace

| Field | Type | Açıklama |
|---|---|---|
| `id` | UUID | Workspace kimliği |
| `name` | String (max 120) | Workspace adı |
| `type` | WorkspaceType | Personal veya team |
| `status` | WorkspaceStatus | Kullanım durumu |
| `createdByUserId` | UUID | Oluşturan kullanıcıya ait audit referansı |
| `createdAt` | Instant | Oluşturulma zamanı |
| `updatedAt` | Instant | Son güncellenme zamanı |
| `version` | Long | Optimistic concurrency kontrolü |

`WorkspaceType`: `PERSONAL`, `TEAM`

`WorkspaceStatus`: `ACTIVE`, `ARCHIVED`

Kurallar:

- Her kullanıcının yalnızca bir personal workspace'i bulunur.
- Personal workspace'e başka kullanıcı eklenemez ve davet gönderilemez.
- Team workspace birden fazla kullanıcı ve rol barındırabilir.
- Workspace adı boş olamaz; sistem genelinde benzersiz olması gerekmez.
- Archived workspace üzerinde yeni project, todo, tag, davet veya assignment oluşturulamaz.
- `createdByUserId` sahiplik kaynağı değildir; sahiplik `WorkspaceMember.role` üzerinden belirlenir.
- Workspace ilk aşamada fiziksel olarak silinmez, archive edilir.

### 5.3 WorkspaceMember

`User` ile `Workspace` arasındaki üyelik ilişkisidir. İlişkinin rol ve yaşam döngüsü bulunduğu için doğrudan many-to-many kullanılmaz.

| Field | Type | Açıklama |
|---|---|---|
| `id` | UUID | Membership kimliği |
| `workspaceId` | UUID | Üye olunan workspace |
| `userId` | UUID | Üye kullanıcı |
| `role` | WorkspaceRole | Workspace içindeki rol |
| `joinedAt` | Instant | Katılım zamanı |
| `addedByUserId` | UUID, nullable | Üyeyi ekleyen kullanıcı |
| `updatedAt` | Instant | Son rol/üyelik değişimi |
| `version` | Long | Concurrent rol değişikliği kontrolü |

`WorkspaceRole`: `OWNER`, `ADMIN`, `MEMBER`

Kurallar:

- Kullanıcı aynı workspace'te yalnızca bir üyeliğe sahip olabilir.
- Her workspace'in en az bir owner'ı bulunmalıdır.
- Son owner ayrılamaz, çıkarılamaz veya daha düşük role geçirilemez.
- Owner, rol yönetebilir ve sahiplik devredebilir.
- Admin üyeleri yönetebilir ancak owner rolünü veremez, düşüremez veya kaldıramaz.
- Member içerik üzerinde izin verilen işlemleri yapabilir; üyelik yönetemez.
- Personal workspace yalnızca sahibi olan kullanıcıya ait owner üyeliğini içerir.
- Workspace ve ilk owner üyeliği aynı transaction içinde oluşturulur.

### 5.4 WorkspaceInvitation

| Field | Type | Açıklama |
|---|---|---|
| `id` | UUID | Davet kimliği |
| `workspaceId` | UUID | Hedef workspace |
| `invitedEmail` | String (max 320) | Davet edilen normalize email |
| `invitedByUserId` | UUID | Daveti oluşturan kullanıcı |
| `acceptedByUserId` | UUID, nullable | Daveti kabul eden kullanıcı |
| `role` | WorkspaceRole | Kabul sonrasında verilecek rol |
| `status` | InvitationStatus | Davet durumu |
| `tokenHash` | String | Davet token'ının güvenli özeti |
| `expiresAt` | Instant | Geçerlilik sonu |
| `createdAt` | Instant | Oluşturulma zamanı |
| `respondedAt` | Instant, nullable | Sonuçlandırılma zamanı |
| `version` | Long | Eş zamanlı sonuçlandırma kontrolü |

`InvitationStatus`: `PENDING`, `ACCEPTED`, `REJECTED`, `CANCELLED`

Kurallar:

- Yalnızca team workspace için davet oluşturulur.
- Daveti owner veya admin gönderebilir.
- Davet üzerinden owner rolü verilmez.
- Aynı workspace ve email için aynı anda yalnızca bir geçerli pending davet bulunur.
- Mevcut üyeye davet gönderilemez.
- Daveti yalnızca email'i eşleşen aktif kullanıcı kabul edebilir.
- Süresi geçen, iptal edilen veya sonuçlandırılmış davet kullanılamaz.
- Ham token saklanmaz.
- Davetin kabulü, membership oluşturulması ve davet durumunun değiştirilmesi tek transaction içinde yapılır.

### 5.5 Project

| Field | Type | Açıklama |
|---|---|---|
| `id` | UUID | Project kimliği |
| `workspaceId` | UUID | Ait olduğu workspace |
| `name` | String (max 120) | Project adı |
| `description` | String (max 1000), nullable | Açıklama |
| `status` | ProjectStatus | Project durumu |
| `createdByUserId` | UUID | Oluşturan kullanıcı |
| `createdAt` | Instant | Oluşturulma zamanı |
| `updatedAt` | Instant | Son güncellenme zamanı |
| `version` | Long | Optimistic concurrency kontrolü |

`ProjectStatus`: `ACTIVE`, `ARCHIVED`

Kurallar:

- Project yalnızca aktif workspace içinde oluşturulur.
- Oluşturan kullanıcı workspace üyesi olmalıdır.
- Normalize project adı aynı workspace içinde benzersizdir.
- Farklı workspace'lerde aynı project adı bulunabilir.
- Archived project'e yeni todo eklenmez.
- Project archive edildiğinde todo'lar fiziksel olarak silinmez.
- Project başka workspace'e taşınmaz.

### 5.6 Todo

| Field | Type | Açıklama |
|---|---|---|
| `id` | UUID | Todo kimliği |
| `workspaceId` | UUID | Ait olduğu workspace |
| `projectId` | UUID, nullable | Bağlı olduğu project |
| `title` | String (max 200) | Başlık |
| `description` | String (max 2000), nullable | Detaylı açıklama |
| `status` | TodoStatus | Mevcut durum |
| `priority` | TodoPriority | Öncelik |
| `dueAt` | Instant, nullable | Son tamamlanma zamanı |
| `completedAt` | Instant, nullable | Tamamlanma zamanı |
| `createdByUserId` | UUID | Oluşturan kullanıcı |
| `createdAt` | Instant | Oluşturulma zamanı |
| `updatedAt` | Instant | Son güncellenme zamanı |
| `version` | Long | Optimistic concurrency kontrolü |

`TodoStatus`: `TODO`, `IN_PROGRESS`, `DONE`, `CANCELLED`

`TodoPriority`: `LOW`, `MEDIUM`, `HIGH`, `URGENT`

Kurallar:

- Todo doğrudan bir workspace'e aittir; project bağlantısı opsiyoneldir.
- Project belirtilirse project ve todo aynı workspace içinde olmalıdır.
- Todo yalnızca aktif workspace ve varsa aktif project içinde oluşturulur.
- Başlık boş olamaz.
- Yeni todo varsayılan olarak `TODO` ve `MEDIUM` değerleriyle başlar.
- `completedAt` yalnızca `DONE` durumunda doludur.
- Tamamlanan todo yeniden açılırsa `completedAt` temizlenir.
- Geçmiş bir due date kabul edilebilir ve todo overdue olarak değerlendirilir.
- Todo başka workspace'e taşınmaz.
- Cross-workspace project, tag veya assignee ilişkisi kurulamaz.
- Status geçişleri domain davranışı olarak korunur; field rastgele set edilmez.

**DB seviyesinde zorunlu kılınacak kurallar:**

Todo ile project'in aynı workspace'te olması basit bir FK ile yakalanamaz. Composite FK kullanılır:

```sql
ALTER TABLE project ADD CONSTRAINT uq_project_id_workspace UNIQUE (id, workspace_id);
ALTER TABLE todo ADD CONSTRAINT fk_todo_project_same_workspace
  FOREIGN KEY (project_id, workspace_id) REFERENCES project (id, workspace_id);
```

`completedAt` ↔ `DONE` tutarlılığı da uygulama kontrolüne bırakılmaz:

```sql
ALTER TABLE todo ADD CONSTRAINT ck_todo_completed_at
  CHECK ((status = 'DONE') = (completed_at IS NOT NULL));
```

### 5.7 Tag

| Field | Type | Açıklama |
|---|---|---|
| `id` | UUID | Tag kimliği |
| `workspaceId` | UUID | Ait olduğu workspace |
| `name` | String (max 50) | Tag adı |
| `color` | String (max 7), nullable | Hexadecimal renk değeri |
| `createdByUserId` | UUID | Oluşturan kullanıcı |
| `createdAt` | Instant | Oluşturulma zamanı |
| `updatedAt` | Instant | Son güncellenme zamanı |
| `version` | Long | Optimistic concurrency kontrolü |

Kurallar:

- Tag project'e değil workspace'e aittir.
- Normalize tag adı aynı workspace içinde benzersizdir.
- Tag adı boş olamaz.
- Color verilmişse standart hexadecimal renk formatında olmalıdır.
- Tag başka workspace'e taşınmaz.
- Tag silinmesi todo'yu silmez; ilişkiler kontrollü kaldırılır.

### 5.8 TodoTag

Todo ile Tag arasındaki açık join entity'dir.

| Field | Type | Açıklama |
|---|---|---|
| `id` | UUID | İlişki kimliği |
| `todoId` | UUID | Etiketlenen todo |
| `tagId` | UUID | Eklenen tag |
| `addedByUserId` | UUID | İşlemi yapan kullanıcı |
| `addedAt` | Instant | Ekleme zamanı |

Kurallar:

- Aynı tag aynı todo'ya bir kez eklenebilir. DB'de `UNIQUE (todo_id, tag_id)` ile korunur.
- Todo ve tag aynı workspace içinde olmalıdır.
- İşlemi yapan kullanıcı workspace üyesi olmalıdır.
- İlişkinin kaldırılması Todo veya Tag kaydını silmez.
- Join entity dış API'de bağımsız resource olarak sunulmaz.

### 5.9 TodoAssignee

Todo ile User arasındaki assignment ilişkisidir.

| Field | Type | Açıklama |
|---|---|---|
| `id` | UUID | Assignment kimliği |
| `todoId` | UUID | Atanan todo |
| `assigneeUserId` | UUID | Atanan kullanıcı |
| `assignedByUserId` | UUID | Atamayı yapan kullanıcı |
| `assignedAt` | Instant | Atama zamanı |

Kurallar:

- Todo birden fazla kullanıcıya atanabilir.
- Kullanıcı aynı todo'ya yalnızca bir kez atanabilir. DB'de `UNIQUE (todo_id, assignee_user_id)` ile korunur.
- Assignee ve işlemi yapan kullanıcı todo'nun workspace'inde aktif üye olmalıdır.
- Assignment cross-workspace olamaz.
- Workspace'ten ayrılan kullanıcıya yeni todo atanamaz.
- Üyelik kaldırılırken mevcut assignment'lar application service tarafından ele alınır.
- Assignment kaldırılması Todo veya User kaydını silmez.
- Todo creator otomatik olarak assignee yapılmaz.

### 5.10 RefreshSession

Security aşamasında eklenen uzun süreli authentication session modelidir.

| Field | Type | Açıklama |
|---|---|---|
| `id` | UUID | Session kimliği |
| `userId` | UUID | Session sahibi |
| `refreshTokenHash` | String | Refresh token özeti |
| `expiresAt` | Instant | Geçerlilik sonu |
| `revokedAt` | Instant, nullable | İptal zamanı |
| `createdAt` | Instant | Oluşturulma zamanı |
| `lastUsedAt` | Instant, nullable | Son kullanım zamanı |
| `userAgent` | String, nullable | İstemci bilgisi |
| `ipAddress` | String, nullable | İstemci adresi |
| `version` | Long | Concurrent refresh kontrolü |

Kurallar:

- Ham refresh token tutulmaz.
- Süresi geçen veya revoke edilen session kullanılamaz.
- Refresh token rotation uygulanır.
- Kullanılmış eski token'ın tekrar kullanılması güvenlik olayı olarak değerlendirilir.
- Logout ilgili session'ı revoke eder.
- Deaktive veya suspend edilmiş kullanıcı session yenileyemez.
- IP ve user-agent yalnızca güvenlik sinyalidir; authentication kanıtı değildir.

### 5.11 AuditLog

Güvenlik ve iş açısından anlamlı olayların append-only kaydıdır.

| Field | Type | Açıklama |
|---|---|---|
| `id` | UUID | Audit kimliği |
| `workspaceId` | UUID, nullable | İlgili workspace |
| `actorUserId` | UUID, nullable | İşlemi yapan kullanıcı |
| `action` | AuditAction | İşlem türü |
| `resourceType` | String | Etkilenen kaynak türü |
| `resourceId` | UUID, nullable | Etkilenen kaynak kimliği |
| `details` | JSON-compatible object, nullable | Güvenli ek bilgiler |
| `correlationId` | String, nullable | Request/işlem zinciri kimliği |
| `occurredAt` | Instant | Olay zamanı |

Kurallar:

- Audit kayıtları güncellenmez.
- Kullanıcı deaktive edilse bile geçmiş korunur.
- Password, token, secret veya gereksiz kişisel veri kaydedilmez.
- Bütün resource türleriyle polymorphic JPA association kurulmaz.
- `details` PostgreSQL `jsonb` kolonu olarak tutulur. JPA tarafında Hibernate 6'nın
  `@JdbcTypeCode(SqlTypes.JSON)` desteği kullanılır; ayrı bir custom type kütüphanesi eklenmez.
  Mapping hedefi serbest `Map<String, Object>` değil, olay türüne göre dar bir DTO'dur.
- Her sıradan CRUD değil, anlamlı güvenlik ve business olayları kaydedilir.

Örnek olaylar: `WORKSPACE_CREATED`, `MEMBER_ADDED`, `MEMBER_REMOVED`, `MEMBER_ROLE_CHANGED`, `INVITATION_ACCEPTED`, `PROJECT_ARCHIVED`, `TODO_ASSIGNED`, `TODO_COMPLETED`, `SESSION_REVOKED`.

## 6. İlişki Özeti

| Kaynak | İlişki | Hedef |
|---|---|---|
| User | N–N (`WorkspaceMember`) | Workspace |
| Workspace | 1–N | WorkspaceInvitation |
| Workspace | 1–N | Project |
| Workspace | 1–N | Todo |
| Workspace | 1–N | Tag |
| Project | 1–N | Todo |
| Todo | N–N (`TodoTag`) | Tag |
| Todo | N–N (`TodoAssignee`) | User |
| User | 1–N | RefreshSession |
| User/Workspace | Mantıksal referans | AuditLog |

Temel aggregate ve ownership sınırı `Workspace` kimliğidir. Workspace'e ait bütün sorgularda ve mutasyonlarda tenant benzeri veri izolasyonu korunur.

## 7. Roller ve Authorization İlkeleri

| İşlem | OWNER | ADMIN | MEMBER |
|---|:---:|:---:|:---:|
| Workspace görüntüleme | ✓ | ✓ | ✓ |
| Workspace ayarlarını güncelleme | ✓ | ✓ | — |
| Workspace archive etme | ✓ | — | — |
| Üye davet etme | ✓ | ✓ | — |
| Member ekleme/çıkarma | ✓ | ✓ | — |
| Owner rolü verme/düşürme | ✓ | — | — |
| Project oluşturma/güncelleme | ✓ | ✓ | ✓ |
| Project archive etme | ✓ | ✓ | — |
| Todo oluşturma/güncelleme | ✓ | ✓ | ✓ |
| Todo silme/iptal etme | ✓ | ✓ | Oluşturan veya izin verilen üye |
| Todo assignment yönetme | ✓ | ✓ | İzin verilen proje üyesi |
| Tag yönetme | ✓ | ✓ | ✓ |

Bu tablo başlangıç politikasıdır. Nihai kontroller yalnızca role bakmaz; kullanıcının aktifliği, workspace üyeliği, resource ownership'i ve resource durumu birlikte değerlendirilir.

- Authentication yalnızca kullanıcının kim olduğunu belirler.
- Authorization her use-case içinde ilgili resource'a erişim iznini belirler.
- Controller seviyesindeki URL kontrolü tek güvenlik katmanı değildir.
- Her workspace-scoped sorgu workspace bağlamını içermeli; yalnızca resource ID ile veri çekip sonradan kontrol etmekten kaçınılmalıdır.
- Resource yokluğu ile erişim yasağının dışarıya nasıl sunulacağı bilgi sızıntısı riskiyle birlikte ele alınır.

## 8. Kritik Use-case ve Transaction Sınırları

Tek transaction gerektiren temel akışlar:

- User + personal workspace + owner membership oluşturma
- Team workspace + owner membership oluşturma
- Daveti kabul etme + membership oluşturma + daveti sonuçlandırma
- Workspace ownership transferi
- Son owner kontrolüyle rol düşürme veya üyelik kaldırma
- Todo oluşturma ve ilk tag/assignment ilişkilerini kurma
- Todo status değişimi ve `completedAt` tutarlılığını sağlama
- Refresh token rotation

Kurallar:

- Transaction mümkün olduğunca application/service use-case sınırında başlar.
- Controller transaction yönetmez.
- External network çağrıları bilinçsizce database transaction içinde tutulmaz.
- Read use-case'lerde uygun olduğunda read-only transaction kullanılır.
- Optimistic locking hataları genel 500 hatasına dönüşmez; anlamlı conflict davranışı tasarlanır.
- Business invariant yalnızca ön kontrolle bırakılmaz; mümkün olan yerde veritabanı bütünlüğüyle de desteklenir.

## 9. API Tasarım İlkeleri

- REST resource'ları entity'lerin birebir dışa açılmış hâli değildir.
- Request ve response DTO'ları persistence entity'lerinden ayrılır.
- Entity doğrudan API response olarak döndürülmez.
- Create işlemleri uygun olduğunda `201 Created`, delete işlemleri uygun olduğunda `204 No Content` döndürür.
- Validation hataları, business rule ihlalleri, bulunamayan resource ve conflict durumları tutarlı error contract kullanır.
- Pagination zorunlu liste endpoint'lerinde sınırsız collection döndürülmez.
- Sorting alanları allow-list ile sınırlandırılır.
- Workspace-scoped endpoint'lerde workspace bağlamı açık tutulur.
- API versioning gerçek ihtiyaç oluşmadan eklenmez; ancak public contract değişiklikleri bilinçli yönetilir.

Örnek resource grupları:

```text
/auth
/users/me
/workspaces
/workspaces/{workspaceId}/members
/workspaces/{workspaceId}/invitations
/workspaces/{workspaceId}/projects
/workspaces/{workspaceId}/todos
/workspaces/{workspaceId}/tags
```

Kesin endpoint sözleşmeleri ilgili feature uygulanmadan önce ayrıca tasarlanır.

## 10. Persistence ve SQL İlkeleri

- Schema değişikliklerinin tek kaynağı versioned Flyway migration'lardır.
- Hibernate production schema'sını oluşturmaz; uygulama schema'yı validate eder.
- Nullability, uniqueness, referential integrity ve güvenli durum kuralları mümkün olduğunda veritabanında da korunur.
- JPA cascade seçenekleri varsayımla değil, yaşam döngüsü sahipliğine göre seçilir.
- `CascadeType.ALL` ve eager fetching varsayılan tercih değildir.
- Collection ilişkilere kontrolsüz `EAGER` uygulanmaz.
- Liste sorgularında N+1, pagination ve fetch join etkileşimi incelenir.
- Index'ler tahminle değil sorgu biçimleri ve `EXPLAIN ANALYZE` sonuçlarıyla değerlendirilir.
- Timestamp'ler uygulama genelinde `Instant`/UTC mental modeliyle ele alınır.
- UUID üretim stratejisi migration ve persistence yaklaşımıyla tutarlı seçilir.
- Normalizasyon kuralları application ve database davranışları arasında çelişmemelidir.

## 11. Validation ve Error Handling

- DTO validation, request biçimi ve temel input sınırlarıyla ilgilenir.
- Business validation application/domain katmanında yapılır.
- Database constraint'leri yarış koşullarına karşı son bütünlük garantisini sağlar.
- Aynı kural gereksiz yere farklı katmanlarda farklı mesajlarla çoğaltılmaz.
- Global exception handling tek tip error response üretir.
- Domain/business exception'lar HTTP kavramlarına doğrudan bağımlı olmaz.
- Log'larda stack trace ve güvenli teknik bağlam korunurken API response hassas iç detay sızdırmaz.

## 12. Security Yaklaşımı

Security, temel domain ve persistence akışı anlaşıldıktan sonra eklenir.

Hedef model:

- Email/password authentication
- Güvenli password hashing
- Kısa ömürlü JWT access token
- Kalıcı depoda hash'li, rotate edilen refresh token/session
- Stateless access-token doğrulaması
- Workspace membership ve resource ownership tabanlı authorization
- Security failure'ları için tutarlı `401 Unauthorized` ve `403 Forbidden` davranışı

Spring Security entegrasyonu yalnızca filter eklemek değildir. Aşağıdaki kavramlar anlaşılmadan implementasyona geçilmez:

- `SecurityFilterChain`
- Authentication ile authorization ayrımı
- `AuthenticationManager` ve `AuthenticationProvider`
- `UserDetailsService` veya seçilen kullanıcı yükleme yaklaşımı
- `PasswordEncoder`
- `SecurityContext`
- JWT doğrulama filter'ının yeri ve sorumluluğu
- Method/use-case seviyesinde resource authorization
- CSRF, CORS ve session policy kararlarının API bağlamındaki gerekçesi
- Refresh token rotation ve token theft riskleri

## 13. Testing Stratejisi

| Kapsam | Varsayılan araç | Amaç |
|---|---|---|
| Domain/service unit | JUnit + Mockito | Business branching ve interaction |
| Controller slice | MockMvc | HTTP contract, serialization, validation |
| Repository/persistence | PostgreSQL Testcontainers | Gerçek SQL, mapping ve constraint davranışı |
| Security | Spring Security Test | Authentication ve authorization kuralları |
| Integration | Spring Boot + Testcontainers | Kritik uçtan uca backend akışları |

İlkeler:

- Her sınıf için test yazılmaz; riskli davranış ve sözleşme test edilir.
- Repository testlerinde H2, PostgreSQL yerine varsayılan olarak kullanılmaz.
- Mock sayısı arttıkça testin implementation detail'e bağlanıp bağlanmadığı sorgulanır.
- Happy path kadar authorization, conflict, concurrency ve invalid state senaryoları da ele alınır.
- Testler birbirine ve çalışma sırasına bağımlı olmaz.
- Migration'ların temiz PostgreSQL üzerinde baştan sona çalışması doğrulanır.

## 14. Observability ve Production Davranışı

- Structured ve anlamlı logging
- Request correlation ID
- Hassas veri maskeleme
- Actuator health/readiness endpoint'leri
- Uygulama ve JVM metrikleri
- Kritik business ve security olayları için ölçüm/audit
- Environment tabanlı configuration
- Secret'ların source control dışında tutulması
- Graceful shutdown
- Tutarlı timeout ve connection pool ayarları
- Container image ve local Docker Compose ortamı

Tracing, özel dashboard'lar ve ileri seviye alerting gerçek kullanım senaryosu oluştuğunda eklenir.

## 15. Geliştirme Aşamaları

### Faz 0 — Temel kurulum

- Spring Boot projesi
- PostgreSQL ve Docker Compose
- Flyway başlangıcı
- Configuration profilleri
- İlk smoke test

### Faz 1 — Todo çekirdeği

**Sıralama kararı:** `Todo.workspaceId` ve `Todo.createdByUserId` non-nullable'dır. Bu yüzden
Faz 1, `user` ve `workspace` tablolarını *minimum hâliyle* içerir — nullable kolon açıp sonra
sıkılaştırmak yerine, ve Todo'yu köksüz bırakmak yerine.

Faz 1'e giren:

- `user` ve `workspace` tabloları (§5.1, §5.2'deki kolonlar; `passwordHash` hariç)
- Sabit UUID'li seed migration: bir dev user + ona ait bir personal workspace
- Todo schema ve entity, **gerçek non-nullable FK'lerle**
- Repository, service ve controller akışı
- DTO, validation ve error handling
- Temel CRUD ve status transition
- İlk unit/controller/repository testleri

Faz 1'e girmeyen (Faz 3'e kalan): `WorkspaceMember`, roller, davetler, personal workspace
teklik kuralı, User ve Workspace API'leri, çoklu kullanıcı semantiği.

Faz 1-2 boyunca `workspaceId` service metodlarına **parametre olarak** geçer. Faz 5'te aynı
değer `SecurityContext`'ten gelmeye başlar; metod imzaları değişmez.

**Gerekçe:** Workspace scoping birinci günden itibaren her sorguda bulunur. Faz 3 böylece
"scoping'i geriye dönük ekleme" refactor'ü değil, mevcut scoping'in üstüne üyelik ve rol
kuralları ekleme işi olur.

### Faz 2 — Sorgulama ve SQL derinliği

- Pagination, sorting ve filtering
- Derived query, JPQL ve gerektiğinde native query
- Index değerlendirmesi
- N+1 ve fetch stratejileri
- `EXPLAIN ANALYZE`

### Faz 3 — Workspace ve collaboration

- User ve Workspace'in tam hâli (API, lifecycle, status yönetimi)
- WorkspaceMember ve roller
- Personal/team workspace kuralları (teklik index'i dahil)
- Project
- Tag ve TodoTag
- TodoAssignee
- Faz 1'de kurulan workspace scoping'in üstüne membership tabanlı erişim kuralları

### Faz 4 — Davet ve rol yönetimi

- WorkspaceInvitation
- Davet state transition'ları
- Üye ve owner yönetimi
- Concurrency ve transaction senaryoları

### Faz 5 — Spring Security

- User credentials
- Authentication pipeline
- Access ve refresh token
- Resource-based authorization
- Security testleri

### Faz 6 — Production backend

- AuditLog
- Structured logging ve correlation ID
- Actuator ve metrics
- Containerization ve CI
- Configuration/security hardening
- Kritik integration ve concurrency testleri

Fazlar yol gösterir; değişmez sprint planı değildir. Bir sonraki faza geçmeden önce mevcut fazın davranışı anlaşılmış ve temel testleri yazılmış olmalıdır.

## 16. Definition of Done

Bir feature tamamlanmış sayılmadan önce:

- Business davranışı ve kapsamı anlaşılmıştır.
- API contract nettir.
- Migration ve entity mapping uyumludur.
- Authorization sınırı tanımlıdır.
- Transaction boundary bilinçli seçilmiştir.
- Temel happy path ve kritik failure senaryoları test edilmiştir.
- Hatalar tutarlı API contract'a dönüşür.
- Log'lara hassas veri yazılmaz.
- Gereksiz abstraction veya dependency eklenmemiştir.
- Kod, ilgili feature'ın öğrenme hedefini gizleyecek kadar otomatikleştirilmemiştir.

## 17. Mimari Karar Disiplini

- Bu doküman **hedefi** gösterir. Projenin **şu anki** hâli `STATUS.md`'dedir.
  Mevcut faz, açık task, uygulanmış migration'lar ve bilinen teknik borçlar orada tutulur.
- Bu doküman baştan sona okunmaz; ilgili bölüm aranarak okunur.
- Bu doküman hedef yapıyı gösterir; bütün yapılar ilk günde implement edilmez.
- Yeni teknoloji veya abstraction somut problem, ölçüm veya öğrenme hedefiyle gerekçelendirilir.
- Mimari karar değişirse neden değiştiği dokümana eklenir.
- Kod ile doküman çelişirse çelişki görünür hâle getirilir; sessizce biri doğru kabul edilmez.
- Agent ve geliştirici bu dokümanı task üretimi ve review için ortak referans olarak kullanır.
