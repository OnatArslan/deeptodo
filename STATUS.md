# DeepToDo — Status

> `PROJECT_SPEC.md` target architecture/scope'u gösterir.
> Bu dosya repository'nin şu anki gerçek durumunu gösterir.
> Coherent implementation task tamamlandığında güncellenir.

## Last updated

2026-09-05

## Current phase

**Phase 0 — Bootstrap**

Goal:

```text
Spring Boot application
+ PostgreSQL local Docker Compose
+ Flyway
+ configuration
+ successful startup/manual smoke
```

## Active task

### DT-001 — Bootstrap baseline

Status: `OPEN`

Scope:
- project baseline'ını current `PROJECT_SPEC.md` ile hizala
- PostgreSQL local environment
- Flyway baseline
- application config
- application startup
- manual smoke

Do not add business features yet.

## Phase status

| Phase | Status |
|---|---|
| Phase 0 — Bootstrap | 🔄 In progress |
| Phase 1 — Todo Core | ⬜ Not started |
| Phase 2 — Workspace / Collaboration / Querying | ⬜ |
| Phase 3 — Security | ⬜ |
| Phase 4 — Testing + Production + Deployment | ⬜ |

## Completed tasks

_None yet._

## Applied migrations

| Migration | Purpose |
|---|---|
| — | — |

## Current schema

No project business schema recorded yet.

Target schema is defined in `PROJECT_SPEC.md`; only applied Flyway migrations belong here.

## Known technical debt

| ID | Debt | Planned phase |
|---|---|---|
| — | — | — |

## Current learning focus

Phase 0:
- Spring Boot bootstrap/configuration
- PostgreSQL connection
- Flyway lifecycle
- application startup

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
