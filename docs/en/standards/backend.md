# Backend Standards

The backend is a modular monolith: multiple Maven modules form one Spring Boot application. See [Adding a Business Module](/en/development/new-module) for the complete module integration process.

## Modules and dependencies

| Module | Responsibility | Constraint |
| --- | --- | --- |
| `admin-base-core` | Foundation for responses, exceptions, shared configuration, and similar capabilities | Does not depend on specific business modules |
| `admin-system-api` | Capabilities and transport types exposed by the system module to other modules | Contains no Controllers, Mappers, or persistence entities |
| `admin-system-biz` | System business implementation | Exposes capabilities to other modules through interfaces |
| `admin-system-start` | Startup, module assembly, and runtime resources | Aggregates runtime dependencies and database migrations |

New business modules follow `admin-<business>-api` / `admin-<business>-biz`. Calls between modules depend on the other module's API interfaces. Avoid directly calling another business module's Mapper or using HTTP to call business logic within the same process.

API interfaces declare only the business capabilities actually needed. They must not extend MyBatis-Plus `IService` to expose a complete set of generic CRUD methods. Prefer managing dependency versions in the parent project. New modules using Lombok must still declare the dependency explicitly; `provided` dependencies are not automatically available as transitive dependencies for compilation.

## Packages and naming

The package prefix is `com.maplehush.admin`. Organize packages by business function, then divide them into directories such as `controller`, `service`, `service.impl`, `mapper`, `entity`, `dto`, and `vo`.

| Type | Existing example | Rule |
| --- | --- | --- |
| Entity | `SysUser`, `SysDept` | System objects use the `Sys` prefix without an `Entity` / `DO` suffix |
| Input object | `SysRoleQueryDTO`, `SysRoleSaveDTO` | Use separate DTOs for input and query conditions |
| HTTP output | `SysRoleVO` | Do not return database entities directly |
| Service interface | `ISysRoleService` | Place in `service/`; callers inject the interface |
| Service implementation | `SysRoleServiceImpl` | Place in `service/impl/` |
| Mapper | `SysRoleMapper` | Handles database access |

Existing business methods use meaningful names such as `listRoles`, `getRole`, `saveRole`, `updateRole`, and `removeRole`. Follow the module's naming conventions; do not replace business actions with vague names such as `handle` or `processData`.

## Responsibilities of the three layers

| Layer | Should handle | Should avoid |
| --- | --- | --- |
| Controller | Request binding, `@Valid`, API permissions, response wrapping | Writing SQL directly or organizing transactions across multiple tables |
| Service | Business rules, target scope, concurrency, transactions, and auditing | Relying on frontend validation or hidden buttons |
| Mapper | Query and update SQL | Deciding whether the current user may perform a business operation |

Use Bean Validation for input length, required values, and numeric ranges. Validate relationships between fields, built-in data protection, authorization scope, and state transitions in Services. Database unique constraints, foreign keys, and check constraints provide the final safeguard.

## Transactions and updates

Place `@Transactional` on public business methods of implementation classes to keep all writes in a business operation consistent. Use `readOnly = true` for read queries when appropriate. Calls made directly between methods on the same object do not pass through the transaction proxy; annotating a private method cannot create a new transaction.

Updates and deletions need explicit target conditions and checks on the affected row count. The following excerpt comes from the existing role update flow. `candidate` is a role entity whose fields have been assigned and validated; authorization, locking, and other business handling are omitted:

```java
if (mapper.updateRole(candidate) != 1) {
    throw notFound();
}
```

This Mapper SQL limits the target using `WHERE id = #{id}` and explicitly sets `update_time = CURRENT_TIMESTAMP`. Entity filling can reuse the existing shared field handler. **When updating with only a Wrapper or custom SQL, maintain `update_time` explicitly**; do not assume entity filling always runs. Application-generated times follow the project's common time zone and retain second precision.

Put writes to multiple tables that must be atomic in the same business transaction. Uniqueness prechecks provide friendly messages, but database constraints must remain to prevent concurrent requests from passing the check simultaneously.

## Queries and SQL

- Prefer Lambda Wrappers for single-table queries, pagination, and ordinary updates, keeping field references typed.
- Use custom SQL as needed for joins, row locks, or complex aggregation; bind parameter values.
- Select sorting fields from a server-side allowlist. Do not concatenate user-provided column names or SQL fragments into statements.
- Limit list result counts; consider pagination, sorting, and indexes together.
- Sort menus, departments, and dictionaries that have `sort` by ascending `sort`, then ascending ID for stable ordering.

Do not use the entire object provided by the frontend directly as an entity update. DTOs specify writable fields, and Services assign them explicitly to prevent changes to protected fields such as built-in flags and authorization versions.

## Shared fields and types

`BaseEntity` provides creation and update times; each entity declares its own primary key. Model authorization relationship tables and certain log tables according to their actual fields; do not add useless fields merely to support inheritance.

Database IDs use `Long`, and external JSON IDs use decimal strings. See [API Standards](./api.md) for reasons and examples. Java time fields follow the business time zone convention, and database time fields use the `_time` suffix.

Java boolean properties do not use an `is` prefix. For example, role property `builtIn` corresponds to `is_builtin`, and dictionary property `defaultItem` corresponds to `is_default`. Specify column names with mapping annotations to avoid ambiguity in serialization and property inference.

## Exceptions and logging

Use `BusinessException` and shared `ErrorCode` values for expected business failures. The central exception handler maps them to HTTP statuses and responses. Do not catch every exception and return success, or expose SQL, stack traces, or raw underlying exception messages to clients.

Use SLF4J placeholders in logs. Record resource IDs, fixed actions, and traceId when needed; avoid logging the same exception stack at multiple layers.

```java
log.info("Role updated: roleId={}", id);
```

Do not log passwords, CAPTCHA answers, password change tokens, access/refresh tokens, complete request bodies, or raw sensitive personal information. Avoid entity implementations that generate an all-field `toString` and leak sensitive values.

For business operations requiring an audit, follow the existing annotations on public implementation methods, as in the current role update:

```java
@Transactional
@OperationLog(value = "system:role:update", targetType = "role", targetIdArgument = 0)
```

`targetIdArgument = 0` identifies the target ID in the first argument. Audits use fixed actions and safe summaries instead of serializing and storing the entire input object.

## Comments and formatting

- Use multiline JavaDoc for classes, interfaces, enums, business methods, fields, and public constants, explaining business meaning and constraints.
- Add `@param`, `@return`, and `@throws` to interface methods as needed; do not pad comments by repeating method names.
- Use braces for all conditional and loop bodies, expanding nonempty blocks across lines.
- Do not use wildcard imports. Use four spaces for Java indentation, UTF-8, LF, and a final newline; follow `backend/.editorconfig`.

```java
/**
 * Retrieves role details; rejects the request if the role does not exist.
 *
 * @param id role identifier
 * @return role details
 */
SysRoleVO getRole(Long id);
```

Reuse explicit Lombok capabilities such as `@Getter`, `@Setter`, and `@RequiredArgsConstructor`. Use only the Hutool `core` and `captcha` modules already included in the project. Do not introduce `hutool-all` or a separate JSON, database, or JWT toolchain.

## Checks

After business changes, use Java 21 and Maven Wrapper in `backend/`:

::: code-group

```powershell [Windows]
.\mvnw.cmd -B -ntp verify
```

```sh [Linux / macOS]
sh mvnw -B -ntp verify
```

:::

Add normal, boundary, rejection, and necessary concurrency tests according to the change. Database integration tests must satisfy their respective runtime conditions. Do not report disabled or skipped tests as passing; a successful ordinary build cannot replace migration validation and real API integration.
