# Database Standards

MySQL is the persistent source of business data. Redis supports existing capabilities such as CAPTCHAs, rate limiting, and caching. Use Flyway for new tables and upgrades; migration files in source code reflect the current structure more accurately than old schema screenshots.

## Naming and types

| Object | Convention and examples | Reason |
| --- | --- | --- |
| Table name | Lowercase with underscores, such as existing `sys_user`, `sys_role`, and `auth_refresh_session` | Names identify the business owner |
| Field | Lowercase with underscores, such as `role_code` and `create_time` | Explicit mapping to Java properties |
| Primary key | Signed `BIGINT` as appropriate for the business model; ordinary entities usually auto-increment | Matches Java Long; relationship tables use keys based on the relationship |
| Status | Define numeric meanings and allowed range, such as `0/1` | Prevents different frontend and backend interpretations |
| Time | Usually `DATETIME`, using the project's common time zone and second precision | Consistent with API time conventions |
| Text | Choose VARCHAR length or TEXT according to its purpose | Document business limits and storage capacity separately |
| Index | Existing `uk_...`, `idx_...` | Names identify uniqueness and query purpose |

Current system tables use InnoDB and `utf8mb4`; the V1 system table collation is `utf8mb4_0900_ai_ci`. Explicitly choose and check collations for new tables, especially case comparison and uniqueness semantics. Avoid making relationship fields difficult to join because their types or collations differ.

Explicitly convert differing frontend and backend field names: for example, database `role_name` corresponds to API `name`. Do not infer public API fields from database column names. See [API Standards](./api.md) for converting database IDs to API strings.

## Shared fields and relationship tables

The `create_time` and `update_time` fields of ordinary business entities correspond to creation and update times in the shared entity. Writes should follow the existing filling mechanism. Wrapper or custom SQL updates must maintain modification time explicitly.

Do not require every table to have an auto-increment ID, status, or modification time. For example, `sys_user_role` uses the composite primary key `(user_id, role_id)` and needs only the authorization relationship and creation time.

Name boolean database fields and Java properties separately to avoid relying on implicit inference: `is_builtin` maps to `builtIn`, and `is_default` maps to `defaultItem`.

## Constraints and concurrency

Database constraints are the final safeguard for data consistency. Current system tables already use primary keys, unique keys, foreign keys, and CHECK constraints. The project has no rule banning all foreign keys.

For example, the V1 user-role relationship uses:

```sql
PRIMARY KEY (user_id, role_id),
KEY idx_sys_user_role_role_user (role_id, user_id),
CONSTRAINT fk_sys_user_role_user
    FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE RESTRICT,
CONSTRAINT fk_sys_user_role_role
    FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE RESTRICT
```

The composite primary key prevents duplicate grants, the reverse index supports finding users by role, and foreign keys prevent orphan relationships. Services must still validate target scope, deletion conditions, and concurrent state; they cannot rely solely on raw database errors from constraints.

Other transactions may insert data between a uniqueness precheck and the final write. Checking before inserting is insufficient. Use database unique keys and the existing exception handling to return safe conflict responses.

## Indexes and queries

Design indexes around actual filtering, join, and sorting conditions, and explain the queries they support. Column order affects the usefulness of a composite index. Do not mechanically add a single-column index for every field.

When adding or changing queries, check:

- Whether returned columns, page size, and sorting are controlled.
- Whether the full table is loaded and paginated in Java memory.
- Whether composite indexes support main filters and sorting, and whether they duplicate existing indexes.
- Whether batch updates and deletions have a limited scope and need processing in batches.
- Whether ordered lists remain stable when sort values are equal.

Analyze execution plans at realistic data volumes for performance questions related to data size. A small sample cannot prove that a query will remain efficient over time.

## Flyway migrations

The shared directory is `backend/admin-system-start/src/main/resources/db/migration/`. The current source contains V1–V10. Check the target environment's own Flyway history to determine which versions have actually run.

Name new scripts `V<version>__<description>.sql`. For example, replace `<N>` in `V<N>__add_notice_index.sql` with an actual unused number. The double underscore is required. When multiple developers work together, coordinate versions before adding scripts to the same migration sequence.

1. Read existing migrations and confirm fields, constraints, and data state.
2. Select a new version unique across the project and write the schema or data upgrade.
3. Check whether existing data satisfies the new constraints; plan backfills and invalid-data handling first if needed.
4. Validate the upgrade in your own development or test database and record the results.
5. Review it together with entity, Mapper, API, and frontend changes.

**Do not directly modify historical migrations that have already run and been shared.** Append corrections in new migrations to avoid checksum mismatches or divergent schemas across environments. Do not run a script manually and then let Flyway repeat the same change, or empty the history table to resolve version conflicts.

See [Database and APIs](/en/development/database-api) for a table creation example for a new feature. Migration scripts must not create the database or hard-code data IDs from other environments or real passwords.

## Reviewing data changes

Before committing, explain why the change is needed, affected tables, existing-data handling, expected execution volume, and compatibility impacts. Adding non-null columns, changing types, deleting fields, and creating unique indexes all require checking whether existing data satisfies the conditions.

Store passwords as secure hashes, never plaintext. Sensitive data such as refresh sessions follows the current hashing and revocation mechanisms; do not add demo tokens or real credentials in migrations. Backups, upgrade windows, and recovery plans are part of actual release preparation. Dropping and recreating a database cannot replace upgrade design.
