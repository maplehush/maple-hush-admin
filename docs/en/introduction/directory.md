# Directory Structure

The main entry points in the current repository are listed below, omitting dependency directories, build artifacts, and similar files. All paths are relative to the product repository root; readers do not need the maintainer's workspace or reference code.

## Repository Root

```text
maple-hush-admin/
├── frontend/                Vben frontend workspace
├── backend/                 Spring Boot backend with four modules
├── docs/                    User-facing VitePress documentation
├── deploy/                  Root Compose overrides and operations scripts
├── 开发文档/                 Internal designs, tasks, and acceptance records
├── .github/workflows/       Current code-check workflows
├── .env.example             Root deployment parameter example
├── docker-compose.yml       Root orchestration entry point
└── README.md                Repository overview
```

`docs/` is the public reading entry point; `开发文档/` supports development and maintenance and must not be published wholesale as the public website. `deploy/` contains existing configuration; its presence does not mean an automatic deployment pipeline has been delivered.

## Frontend Project

```text
frontend/
├── apps/
│   └── web-antdv-next/       Current target application
│       ├── src/
│       │   ├── adapter/     Component, form, and table adapters
│       │   ├── api/         Application requests and API types
│       │   ├── auth/        Login sessions, refresh, and coordination across tabs
│       │   ├── layouts/     Layout integration
│       │   ├── locales/     Application translations
│       │   ├── router/      Routes, guards, and dynamic menu assembly
│       │   ├── store/       Application state
│       │   ├── utils/       Application utilities
│       │   └── views/       Pages
│       │       ├── _core/   Login, personal center, and fallback pages
│       │       ├── dashboard/ Overview pages such as the workbench
│       │       ├── log/     Login and operation logs
│       │       └── system/ Users, roles, menus, departments, and dictionaries
│       ├── package.json
│       └── vite.config.ts
├── packages/                Shared workspace packages
├── internal/                Build and code-check configuration
├── scripts/                 Utilities and frontend deployment scripts
├── pnpm-workspace.yaml      Workspace scope and catalog
├── pnpm-lock.yaml           Resolved dependency versions
└── package.json             Frontend commands and tool requirements
```

Shared `packages/` and application code together form the frontend. New business pages usually belong in the target application. Check shared packages for reusable foundations before implementing them again.

## Backend Project

```text
backend/
├── admin-base-core/
│   └── src/main/java/com/maplehush/admin/
│       ├── common/          Responses, exceptions, entities, auth foundations, storage, etc.
│       └── config/          Shared Redis, MyBatis, and Web configuration
├── admin-system-api/
│   └── src/main/java/com/maplehush/admin/system/api/
│                            Internal contracts for current user, permissions, departments, etc.
├── admin-system-biz/
│   └── src/main/java/com/maplehush/admin/system/
│       ├── auth/            Login, sessions, security chain, and initial bootstrap
│       ├── user/            User and profile business logic
│       ├── role/            Roles and authorization
│       ├── menu/            Menu maintenance and route data
│       ├── dept/            Departments
│       ├── dict/            Dictionaries
│       ├── log/             Login/operation logs and auditing
│       ├── permission/      Permission computation and authorization changes
│       ├── file/            File upload APIs
│       └── docs/            Documentation access policy
├── admin-system-start/
│   ├── src/main/java/com/maplehush/admin/
│   │   ├── AdminApplication.java
│   │   └── config/docs/     Runtime documentation assembly and startup logging
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── application-dev.yml
│   │   ├── application-test.yml
│   │   ├── application-prod.yml
│   │   └── db/migration/    Flyway migrations
│   └── src/test/            Startup module tests and integration verification
├── .mvn/wrapper/            Maven Wrapper configuration
├── deploy/                  Backend standalone deployment support files
├── mvnw / mvnw.cmd          Maven command entry points
├── docker-compose.yml       Standalone backend and dependency orchestration
└── pom.xml                  Aggregate build and version management
```

Business modules are organized by feature. A typical feature contains `controller`, `dto`, `vo`, `service`, `service/impl`, `mapper`, and `entity`. Every feature need not create every subdirectory; organize them by actual responsibility.

Services use `I...Service` interfaces and `...ServiceImpl` implementations, and callers inject the interfaces. The development guide provides specific steps for adding modules.

## Common Code Entry Points

| Goal | Start Here |
| --- | --- |
| Adjust the user page | frontend/apps/web-antdv-next/src/views/system/user/ |
| Find user API requests | frontend/apps/web-antdv-next/src/api/system/user.ts |
| Inspect common request handling | frontend/apps/web-antdv-next/src/api/request.ts |
| Inspect login and session coordination | frontend/apps/web-antdv-next/src/auth/ |
| Inspect dynamic routes | frontend/apps/web-antdv-next/src/router/access.ts |
| Find backend user logic | backend/admin-system-biz/src/main/java/com/maplehush/admin/system/user/ |
| Inspect HTTP documentation assembly | backend/admin-system-start/src/main/java/com/maplehush/admin/config/docs/ |
| Find database migrations | backend/admin-system-start/src/main/resources/db/migration/ |
| Inspect environment configuration | backend/admin-system-start/src/main/resources/application*.yml |
| Inspect response and error definitions | backend/admin-base-core/src/main/java/com/maplehush/admin/common/ |
| Adjust documentation navigation | docs/.vitepress/configs/nav.ts and sidebar.ts |

## Documentation Project

```text
docs/
├── .vitepress/
│   ├── config.ts            Site configuration
│   ├── configs/
│   │   ├── nav.ts           Top navigation
│   │   └── sidebar.ts       Section sidebars
│   └── theme/               Default theme extensions and table styles
├── assets/images/           Chinese and English documentation illustrations
├── en/                      English pages mirroring the section structure
├── introduction/            Four pages in this section
├── getting-started/         Five getting-started pages
├── development/             Six development guide pages
├── standards/               Development standards (six pages)
├── deployment/              Deployment and operations (placeholder index only)
├── release/                 Releases and upgrades (placeholder index only)
├── faq/                     FAQs (guide and three category articles)
├── index.md                 Site homepage
├── package.json             Independent install, development, and build commands
├── pnpm-lock.yaml           Documentation dependency lockfile
├── pnpm-workspace.yaml      Build script permissions
└── README.md                Project maintenance notes, not a public page
```

All seven section directories exist. Project introduction has four articles, getting started has five, development guide and development standards each have six, and FAQs have a guide and three category articles. Deployment/operations and releases/upgrades retain entry points pending completion. Further articles will be added incrementally in their corresponding tasks; empty articles are not created in advance.

Chinese pages use the root path; English pages use `/en/`. The header language menu switches to the corresponding article. Keep both languages and their links in sync when adding articles.
