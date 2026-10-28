# Local Configuration

This page prepares parameters for starting the backend from source. Defaults are local MySQL on 3306, Redis on 6379, frontend on 5999, and backend on 8080; use the actual addresses and credentials of your development environment.

## Create the Development Database First

Connect to MySQL with an account allowed to create databases, then run in the MySQL client:

```sql
CREATE DATABASE maple_hush_admin
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

Run this only if the database does not exist. If a database with that name exists, first confirm its purpose. You can use a different new name and update `MYSQL_DATABASE` accordingly; do not delete or empty the old database.

The backend runtime account must be able to create tables and indexes and read/write data as required by Flyway in this development database. An existing dedicated account is suitable. The `root` account below is only a local development example; production environments use a separate restricted account.

## How Backend Configuration Loads

Configuration files are in `backend/admin-system-start/src/main/resources/`:

| File | Purpose |
| --- | --- |
| application.yml | Shared configuration, authentication, logging, and OSS parameters |
| application-dev.yml | Local development connections and allowed frontend Origins |
| application-test.yml | Test environment |
| application-prod.yml | Production environment |

This chapter activates `dev` and overrides specific parameters through environment variables in the startup terminal.

**Creating `.env` in the repository root or backend does not make `java -jar` read it automatically.** Root `.env` / backend `.env.example` primarily serve Compose. On the frontend, Vite reads the target application's `.env*` files; do not mix the two mechanisms.

## Set Backend Parameters

Run in the terminal that will start the backend, with `backend/` as the current directory. Replace the sample database and Redis passwords before executing. Variables affect only the current terminal and child processes and are not written into source files.

::: code-group

```powershell [PowerShell]
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:MYSQL_HOST = '127.0.0.1'
$env:MYSQL_PORT = '3306'
$env:MYSQL_DATABASE = 'maple_hush_admin'
$env:MYSQL_USERNAME = 'root'
$env:MYSQL_PASSWORD = 'YOUR_ACTUAL_MYSQL_PASSWORD'
$env:REDIS_HOST = '127.0.0.1'
$env:REDIS_PORT = '6379'
$env:REDIS_DATABASE = '0'
$env:REDIS_PASSWORD = 'YOUR_ACTUAL_REDIS_PASSWORD'
$env:REDIS_KEY_PREFIX = 'maple_hush_dev'
$env:SERVER_PORT = '8080'
$env:AUTH_ALLOWED_ORIGINS = 'http://localhost:5999,http://127.0.0.1:5999'
$env:ADMIN_BOOTSTRAP_ENABLED = 'true'
$env:ADMIN_BOOTSTRAP_USERNAME = 'admin'
$env:AUTH_JWT_SECRET = node -e "process.stdout.write(require('node:crypto').randomBytes(32).toString('hex'))"
```

```sh [Bash]
export SPRING_PROFILES_ACTIVE=dev
export MYSQL_HOST=127.0.0.1
export MYSQL_PORT=3306
export MYSQL_DATABASE=maple_hush_admin
export MYSQL_USERNAME=root
export MYSQL_PASSWORD='YOUR_ACTUAL_MYSQL_PASSWORD'
export REDIS_HOST=127.0.0.1
export REDIS_PORT=6379
export REDIS_DATABASE=0
export REDIS_PASSWORD='YOUR_ACTUAL_REDIS_PASSWORD'
export REDIS_KEY_PREFIX=maple_hush_dev
export SERVER_PORT=8080
export AUTH_ALLOWED_ORIGINS='http://localhost:5999,http://127.0.0.1:5999'
export ADMIN_BOOTSTRAP_ENABLED=true
export ADMIN_BOOTSTRAP_USERNAME=admin
export AUTH_JWT_SECRET="$(node -e "process.stdout.write(require('node:crypto').randomBytes(32).toString('hex'))")"
```

:::

Do not leave the `YOUR_ACTUAL_...` placeholders unchanged. Current dev defaults are MySQL username/password `root/123456` and Redis password `123456`. These are only local project defaults; they do not change the accounts or passwords of services you have installed.

The JWT example generates 32 random bytes and converts them to hexadecimal text; you do not need to copy the output. Without a configured JWT secret, `dev` also uses a random key held in the process. Old access tokens may become invalid after a restart, requiring another login. If using your own fixed, controlled secret, meet the minimum 32-byte requirement and do not commit it to Git.

### Redis Connection

`REDIS_DATABASE` selects the Redis logical database, while `REDIS_KEY_PREFIX` distinguishes business keys. Set them separately when multiple development instances share a service. Prefixes may contain only letters, digits, underscores, or hyphens.

If development Redis has no password, explicitly append this option when starting the Jar:

```sh
java -jar admin-system-start/target/admin-system-start-0.0.1-SNAPSHOT.jar --spring.data.redis.password=
```

This overrides the default password in development configuration. In some PowerShell environments, setting an empty environment variable may remove it, so `$env:REDIS_PASSWORD = ''` alone does not establish that the final value is empty.

## Flyway and the Initial Administrator

These initialization responsibilities differ:

| Item | Responsible Party |
| --- | --- |
| Create the database itself | You create it in MySQL beforehand |
| Create business tables, initialize roles/menus, and apply later schema migrations | Flyway runs automatically at application startup |
| Create the first super administrator | The application bootstraps it when the database is empty and ADMIN_BOOTSTRAP_ENABLED is enabled |

Do not manually import internal SQL drafts or repeatedly import Flyway files. Do not arbitrarily alter migrations that have run; their history is stored in `flyway_schema_history`.

For the first empty database, use `ADMIN_BOOTSTRAP_ENABLED=true`. The default account is `admin`, with temporary password **12345678**. You must set a new password on the first-login password-change page. If you changed `ADMIN_BOOTSTRAP_USERNAME`, log in with that account name.

A database with a working administrator does not need bootstrap again; set it to `false`. Re-enabling bootstrap, changing the username parameter, or rebuilding the application does not reset passwords for accounts already in the database.

## Configure OSS Later If Needed

You can omit OSS credentials during initial startup and first complete login, permission, and management-page integration. Without configuration, avatar and file uploads return a dependency-unavailable error; this does not mean the entire application cannot start.

When uploads are needed, set actual parameters in the backend runtime environment:

| Parameter | Purpose |
| --- | --- |
| OSS_REGION | Bucket region |
| OSS_BUCKET | Actual bucket name |
| OSS_PUBLIC_DOMAIN | HTTPS domain matching how objects are actually accessed |
| OSS_OBJECT_PREFIX | Business object prefix |
| OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET | Backend credentials |
| OSS_SECURITY_TOKEN | Token when using temporary credentials |

Do not place these credentials in frontend `VITE_` variables or commit them to the repository.

## Starting from an IDE

Import `backend/pom.xml` and select JDK 21. The main class is `com.maplehush.admin.AdminApplication`, in module `admin-system-start`.

Variables set in a terminal may not reach an already open IDE. When starting from an IDE, put the parameters above in the corresponding run configuration and activate dev. Do not start both the IDE application and Jar on the same port.

Continue to [Startup and First Login](./first-run.md) when configuration is complete.
