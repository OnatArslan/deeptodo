# DeepToDo — Status

> `PROJECT_SPEC.md` target architecture/scope'u gösterir.
> Bu dosya repository'nin şu anki gerçek durumunu gösterir.
> Coherent implementation task tamamlandığında güncellenir.

## Last updated

2026-10-01

## Current phase

**Phase 1 — Todo Core**

Goal:

```text
users + workspaces (minimal, no security fields)
+ fixed dev user/workspace seed
+ todos: CRUD, status transition, optimistic version, hard delete
+ request/response DTO, validation/error contract
+ workspace-scoped queries
```

## Active task

### DT-002 — Users + Workspaces schema and entities

Status: `OPEN`

Scope:
- `V1` migration: `users` (without `password_hash`) and `workspaces`, per spec §7.1–7.2
- `User` and `Workspace` entities mapped to these tables; `ddl-auto: validate` passes
- no repository/service/controller/endpoint yet

## Phase status

| Phase | Status |
|---|---|
| Phase 0 — Bootstrap | ✅ Done |
| Phase 1 — Todo Core | 🔄 In progress |
| Phase 2 — Workspace / Collaboration / Querying | ⬜ |
| Phase 3 — Security | ⬜ |
| Phase 4 — Testing + Production + Deployment | ⬜ |

## Completed tasks

### DT-001 — Bootstrap baseline ✅ (2026-10-01)

- `compose.yaml`: PostgreSQL `17.6`, named volume `postgres-data`, `pg_isready` healthcheck
- `pom.xml`: data-jpa, PostgreSQL driver (runtime), `spring-boot-starter-flyway` + `flyway-database-postgresql`, actuator
- `application.yaml`: env-overridable datasource (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`), `ddl-auto: validate`, `open-in-view: false`
- `src/main/resources/db/migration/` created (empty)
- Manual smoke: app starts, Flyway creates `flyway_schema_history`, `/actuator/health` → `UP`

## Applied migrations

| Migration | Purpose |
|---|---|
| — | — |

## Current schema

No project business schema yet. Only Flyway's `flyway_schema_history` exists.

Target schema is defined in `PROJECT_SPEC.md`; only applied Flyway migrations belong here.

## Known technical debt

| ID | Debt | Planned phase |
|---|---|---|
| — | — | — |

## Current learning focus

Phase 1:
- Flyway migration writing (constraints, CHECK, naming conventions)
- JPA entity mapping vs. schema validation
- UUID v7 generation, `Instant` / `timestamptz`, enum as string

## Locked scope decisions

Do not casually reopen these during implementation:

- one generic Workspace type
- roles: OWNER / MEMBER
- multiple OWNER allowed
- last OWNER invariant
- multi-assignee Todo remains
- Todo Project relationship optional
- Tag + explicit TodoTag remains
- TodoAssignee explicit join entity
- register does not auto-create Workspace
- tests mainly Phase 4
- no AuditLog in v1
- no Redis/Kafka/microservices
- final v1 includes Docker + public HTTPS deployment
