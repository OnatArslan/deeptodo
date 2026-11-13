# DeepToDo Spec — 06 API ve hata sözleşmesi

> Hata sözleşmesi **W2**'de kurulur; uçlar ilgili adımlarda eklenir. Kurallar entity dosyalarında, burada yalnız şekiller.

## Genel

- Kök yol `/api`. İstek ve cevap gövdeleri JSON, alan adları camelCase.
- Zamanlar ISO-8601 UTC: `"2026-11-12T10:15:30Z"`. Saatsiz ya da saat dilimsiz tarih kabul edilmez (400).
- Id'ler UUID metni. Path'teki id UUID değilse 400.
- Oluşturma uçları 201 ve `Location: /api/<kaynak>/<id>` döner, gövdede oluşturulan kayıt durur.
- `/api/auth/register`, `/login`, `/refresh`, `/logout` ve `/actuator/health` dışındaki her uç
  `Authorization: Bearer <access token>` ister (S1'den itibaren).
- Cevapta entity yoktur; her cevap bir DTO'dur (record).

## Uçlar

### Auth ve kullanıcı ([02-kimlik-dogrulama](02-kimlik-dogrulama.md))

| Uç | İstek gövdesi | Başarı | Hatalar |
|---|---|---|---|
| `POST /api/auth/register` | `email`, `password`, `displayName` | 201 `User` (Location yok) | 400, 409 email kayıtlı |
| `POST /api/auth/login` | `email`, `password` | 200 `Login` + `Set-Cookie` | 400, 401 |
| `POST /api/auth/refresh` | — (cookie) | 200 `Login` + `Set-Cookie` | 401 |
| `POST /api/auth/logout` | — (cookie) | 204 + cookie silme | — |
| `GET /api/users/me` | — | 200 `User` | 401 |
| `DELETE /api/users/me` | — | 204 + cookie silme | 401 |

### Todo ([03-todo](03-todo.md))

| Uç | İstek | Başarı | Hatalar |
|---|---|---|---|
| `POST /api/todos` | `title`, `description?`, `priority?`, `dueAt?`, `projectId?` | 201 `Todo` | 400; 404 project; 409 arşivli project |
| `GET /api/todos` | query: [03-todo → Listeleme](03-todo.md) | 200 `Page<Todo>` | 400 geçersiz sort / parametre |
| `GET /api/todos/{id}` | — | 200 `Todo` | 404 |
| `PATCH /api/todos/{id}` | `version` + değişen alanlar (`title`, `description`, `status`, `priority`, `dueAt`, `projectId`) | 200 `Todo` | 400; 404; 409 geçersiz geçiş / version / arşivli project |
| `DELETE /api/todos/{id}` | — | 204 | 404 |
| `PUT /api/todos/{id}/tags/{tagId}` | — | 204 | 404 todo ya da tag |
| `DELETE /api/todos/{id}/tags/{tagId}` | — | 204 | 404 todo ya da tag |

### Project ([04-project](04-project.md))

| Uç | İstek | Başarı | Hatalar |
|---|---|---|---|
| `POST /api/projects` | `name`, `description?` | 201 `Project` | 400, 409 ad |
| `GET /api/projects` | `?status=ACTIVE\|ARCHIVED` (varsayılan ACTIVE) | 200 `Project[]` | |
| `GET /api/projects/{id}` | — | 200 `Project` | 404 |
| `PATCH /api/projects/{id}` | `name?`, `description?` | 200 `Project` | 400, 404, 409 ad / arşivli |
| `POST /api/projects/{id}/archive` | — | 204 | 404 |

### Tag ([05-tag](05-tag.md))

| Uç | İstek | Başarı | Hatalar |
|---|---|---|---|
| `POST /api/tags` | `name`, `color?` | 201 `Tag` | 400, 409 ad |
| `GET /api/tags` | — | 200 `Tag[]` | |
| `PATCH /api/tags/{id}` | `name?`, `color?` | 200 `Tag` | 400, 404, 409 ad |
| `DELETE /api/tags/{id}` | — | 204 | 404 |

## Cevap şekilleri

```text
User     { id, email, displayName, createdAt }
Login    { accessToken, tokenType: "Bearer", expiresIn: 900 }
Todo     { id, title, description, status, priority, dueAt, completedAt,
           project: { id, name } | null,
           tags: [ { id, name, color } ],          ← ada göre sıralı
           version, createdAt, updatedAt }
Project  { id, name, description, status, createdAt, updatedAt }
Tag      { id, name, color, createdAt, updatedAt }
Page<T>  { content: [T], page, size, totalElements, totalPages }
```

- `User` cevabında `passwordHash` ve `status` yok.
- `Page<T>` **kendi DTO'muzdur**. Spring Data'nın `Page` nesnesini doğrudan JSON'a yazmak, iç yapısını (sort,
  pageable alanları) API sözleşmesi yapar; Spring Data bunun için uyarı da loglar.
- Değeri olmayan isteğe bağlı alanlar `null` olarak yazılır (alan atlanmaz). İstemci her zaman aynı şekli görür.

## Hata sözleşmesi (W2, S1)

Her hata `application/problem+json` ve `ProblemDetail` şeklindedir:

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "A project named 'Tez' already exists",
  "instance": "/api/projects"
}
```

Validation hatalarında ayrıca hangi alanın neden hatalı olduğu:

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation failed",
  "instance": "/api/todos",
  "errors": [
    { "field": "title", "message": "must not be blank" },
    { "field": "dueAt", "message": "must be an ISO-8601 instant" }
  ]
}
```

| Durum | Status | Kim üretir |
|---|---|---|
| Validation, bozuk JSON, geçersiz parametre / UUID / enum | 400 | MVC + `@RestControllerAdvice` |
| Token yok / geçersiz / süresi dolmuş | 401 + `WWW-Authenticate: Bearer …` | Security filter zinciri |
| Login başarısız, geçersiz refresh token | 401 | auth uçlarının handler'ı |
| Kayıt yok ya da başkasının | 404 | `@RestControllerAdvice` (domain exception) |
| Yanlış method (`GET /api/auth/login`) | 405 | MVC |
| Tekillik, geçersiz geçiş, arşivli project, version çakışması | 409 | `@RestControllerAdvice` |
| Beklenmeyen hata | 500, `detail` genel bir cümle | `@RestControllerAdvice` |

Kurallar:
- **Domain exception'ları HTTP bilmez.** Service `ResourceNotFoundException` fırlatır, 404'e çeviren advice'tır. Service
  bir gün HTTP olmayan bir yerden (bir consumer, bir job) çağrılırsa aynı exception anlamlı kalır.
- 500'de stack trace, SQL ya da sınıf adı **dışarı sızmaz**; ayrıntı yalnız log'dadır.
- DB tekillik ihlali (yarış) 409'a çevrilirken constraint adına bakılır. Her `DataIntegrityViolationException` 409 değildir;
  tanınmayan bir ihlal 500'dür.
- Filter zincirindeki hatalar (401 / 403) MVC'ye ulaşmadan oluşur. `@RestControllerAdvice` onları göremez; Security'nin
  kendi handler'ı aynı `ProblemDetail` şeklini üretir (S1).
