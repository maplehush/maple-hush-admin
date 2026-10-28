# Development Standards

This section describes the conventions to follow when extending Maple Hush Admin. For your first feature, follow the [Development Guide](/en/development/) to connect the module, API, page, and permissions, then use this section to review the implementation.

## Reading order

| Page | Main questions addressed |
| --- | --- |
| [Backend Standards](./backend.md) | Module dependencies, layers, naming, transactions, validation, and logging |
| [Frontend Standards](./frontend.md) | Directories, component adapters, types, forms, internationalization, and formatting |
| [API Standards](./api.md) | Requests, responses, pagination, IDs, error codes, and authentication |
| [Database Standards](./database.md) | Fields, constraints, indexes, and Flyway migrations |
| [Git Collaboration](./git.md) | Branches, commits, checks, and sensitive file management |

## Scope

- Backend conventions apply to Java, SQL, and configuration in `backend/`.
- Frontend conventions apply to `frontend/`; the current target application is `apps/web-antdv-next`.
- `docs/` is a separate documentation project with its own dependencies, lockfile, and build commands.
- Reference projects in the workspace are for consultation; they do not establish that an API or feature exists in the current product.

When APIs, fields, or configuration change, update callers and the relevant usage documentation together. If a new rule differs from existing code, first confirm its impact and agree on a consistent convention so neither side has to guess.

## Shared principles

### Keep responsibilities clear

Controllers handle HTTP boundaries, Services contain business rules, and Mappers handle persistence. Frontend pages handle interaction, API files handle requests, and adapters handle component contracts. Extract shared logic when there is a real reuse requirement; avoid adding multiple forwarding layers for simple operations.

### Use existing capabilities

Reuse `requestClient` for ordinary business requests, Vben wrappers for forms and lists, shared error codes for failures, and Flyway for database upgrades. New features can then continue to use the existing authentication, exception handling, and interaction mechanisms.

### Enforce validation and permissions on the backend

Frontend validation improves input usability, and hiding buttons improves the user experience. The backend must still validate parameters, operation permissions, target data scope, and concurrency constraints. Constructing an HTTP request must not bypass these restrictions.

### Record results supported by evidence

Commit descriptions must state what changed, the checks actually run, and their results. Do not describe builds, tests, migrations, or deployments as passing if they were not run. Tutorial examples also require validation after integration into your own project.

## Before committing

| Check | What to confirm |
| --- | --- |
| Feature scope | The change solves a clear problem without unrelated refactoring |
| API consistency | Paths, fields, ID types, permission codes, and responses agree with callers |
| Data security | Authorization is enforced; sensitive values do not enter responses, logs, or Git |
| Data changes | New migration versions do not conflict, historical migrations remain unmodified, and constraints and indexes are appropriate |
| Complete interaction | Loading, failure, repeated submission, empty data, and language switching are handled |
| Check results | Run and record checks appropriate to the change; tests cover critical behavior and rejection paths |
| Documentation | Changes to usage, configuration, or contracts are reflected in documentation |

## Run checks in the relevant project

See the respective standards pages for frontend and backend commands, and [Git Collaboration](./git.md) for current CI coverage. For documentation changes alone, run this from the repository root:

```sh
pnpm --dir docs build
```

Then preview the affected pages and check navigation, links, code blocks, and tables. Writing documentation does not require starting the database, Redis, or business applications.
