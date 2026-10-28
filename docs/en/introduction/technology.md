---
pageClass: technology-page
---

# Technology Stack

The tables below are a project dependency snapshot checked on **2026-10-28**. Frontend versions come from the resolved versions in `frontend/pnpm-lock.yaml`; backend versions come from the Maven dependency tree. These are not claims about the latest upstream releases.

## Frontend

The target application is `frontend/apps/web-antdv-next`.

| Technology | Version | Purpose | Notes |
| --- | --- | --- | --- |
| Vben Admin target application | 5.8.0 | Administration layouts, permissions, and application foundations | Version in the target application's package.json; the frontend root package remains 5.7.0, which is not this project's release version |
| Vue | 3.5.41 | Reactive interfaces and components | Actual version locked for the target application |
| Vue Router | 5.2.0 | Routing and page navigation | Generates accessible routes together with backend menus |
| Pinia | 4.0.3 | Application state management | Used by shared workspace stores and the target application |
| antdv-next | 1.5.2 | UI components | Component library used by the current target application |
| Axios | 1.19.0 | HTTP requests | Wrapped by `@vben/request`; the application integrates common responses and session handling |
| Vue I18n | 11.4.10 | Internationalization | Wrapped by `@vben/locales` |
| VueUse | 14.4.0 | Vue composition utilities | Corresponds to `@vueuse/core` |
| Tailwind CSS | 4.3.3 | Styling utilities | Shared frontend styles and layouts |
| VXE Table | 4.21.2 | Data tables | Used through shared plugins and the application adapter layer |
| ECharts | 6.1.0 | Charts | Shared plugins provide chart capabilities; this does not mean every chart-based business feature is implemented |
| Day.js | 1.11.23 | Date handling | Target application dependency |

Declared version ranges may differ from resolved versions. For example, the catalog declares `antdv-next` as `^1.4.5`, while the lockfile resolves it to `1.5.2`. Reinstall using the repository lockfile rather than manually installing dependencies from the numbers on this page.

## Backend

The backend uses Java 21, Spring MVC, and a relational database, and delivers one executable Jar.

| Technology | Version | Purpose | Notes |
| --- | --- | --- | --- |
| Java | 21 | Compilation and runtime | java.version / compiler release in the parent POM |
| Spring Boot | 4.1.1 | Application assembly and auto-configuration | Locked in the backend parent project |
| Spring Framework | 7.0.9 | Web MVC, dependency injection, and transactions | Managed by Spring Boot; actual dependency tree resolution |
| Spring Security | 7.1.1 | Authentication and API authorization | Managed by Spring Boot |
| MyBatis-Plus | 3.5.17 | Data access and pagination | Uses the Boot 4 starter and JSQLParser extension |
| MyBatis | 3.5.19 | SQL mapping | Introduced by MyBatis-Plus |
| Spring Data Redis | 4.1.1 | Redis access | Managed by Spring Boot |
| Lettuce | 7.5.2.RELEASE | Redis client | Managed by Spring Boot; distinct from the Redis server version |
| Flyway | 12.4.0 | Database version migrations | core and mysql extensions, managed by Spring Boot |
| MySQL Connector/J | 9.7.0 | JDBC driver | Managed by Spring Boot; distinct from the MySQL server version |
| HikariCP | 7.0.2 | Database connection pool | Managed by Spring Boot |
| Hibernate Validator | 9.1.3.Final | Request parameter validation | Introduced by the Spring Boot validation starter |
| Nimbus JOSE + JWT | 10.9.1 | Underlying JWT encoding, decoding, and validation | Introduced by Spring Security OAuth2 JOSE |
| Lombok | 1.18.48 | Compile-time boilerplate generation | Version and annotation processor explicitly configured in the project |
| Hutool | 5.8.48 | General utilities and image CAPTCHAs | Uses hutool-core and hutool-captcha |
| Knife4j Next | 5.7.9 | Online API documentation | Boot 4 starter; enabled only in development/test environments |
| springdoc-openapi | 3.0.3 | OpenAPI generation | Introduced by the Knife4j starter |
| Swagger Jakarta annotations | 2.2.55 | API description annotations | Explicitly configured in the project |
| Alibaba Cloud OSS SDK V2 | 0.6.0 | Object storage access | Corresponds to alibabacloud-oss-v2; the module POM specifies the version |
| TwelveMonkeys WebP | 3.15.3 | WebP image reading support | Used for image processing and validation |

Spring Boot 4's application JSON stack coexists with JSON stacks used by some dependencies: the dependency tree contains Jackson 3 `tools.jackson` **3.1.5** and OpenAPI / OSS-related Jackson 2 `com.fasterxml.jackson` **2.21.5** (databind). Their coordinates differ, so they cannot be described as one unified Jackson version.

## Data Storage and External Services

| Technology | Version | Purpose | Notes |
| --- | --- | --- | --- |
| MySQL | 8.4 LTS | Persistent users, roles, menus, dictionaries, logs, refresh sessions, and other data | The default Compose image is mysql:8.4; this is the deployment target, and the running image digest determines the actual patch version |
| Redis | 7.4 | CAPTCHAs, rate limits, user snapshots, permission caches, and similar state | The default Compose image is redis:7.4-alpine; temporary state does not replace persistent MySQL data |
| Alibaba Cloud OSS | N/A | Avatar and image object storage | A managed service has no corresponding project dependency version; see the backend table for the client SDK version |

Historical local development checks used MySQL **8.0.24** and Redis **3.0.504**. These are not recommended versions for new deployments, nor do those checks imply authentication, migrations, and regression acceptance across all version combinations.

## Development, Build, and Documentation Tools

| Tool | Version | Purpose | Notes |
| --- | --- | --- | --- |
| Node.js (frontend) | ^22.18.0 or ^24.12.0 | Frontend toolchain | Allowed engines range; .node-version / CI fixes 24.16.0 |
| pnpm (frontend) | 11.16.0 | Frontend workspace dependency management | Fixed by packageManager; do not apply the docs pnpm version directly |
| Vite (frontend) | 8.3.0 | Development and builds | Resolved in the frontend lockfile |
| TypeScript (frontend) | 6.0.3 | Type system | Resolved in the frontend lockfile |
| vue-tsc | 3.3.11 | Vue type checking | Resolved in the frontend lockfile |
| Turborepo | 2.10.13 | Workspace task orchestration | Resolved in the frontend lockfile |
| ESLint / Oxfmt | 10.10.0 / 0.65.0 | Code checking and formatting | Resolved in the frontend lockfile |
| Vitest | 4.1.11 | Frontend unit tests | Resolved in the frontend lockfile |
| Maven | 3.9.9 | Backend builds | Fixed by the Maven Wrapper distributionUrl |
| JUnit Jupiter | 6.0.3 | Backend tests | Managed by Spring Boot test dependencies |
| Docker Compose | At least 2.24.4 | Container orchestration | The root configuration uses include; a minimum requirement does not claim every version has been tested |
| GitHub Actions | N/A | Repository CI | Existing workflows check code; no unified product version number |
| VitePress | 1.6.4 | Documentation website | Stable version fixed in the independent docs project |
| Node.js / pnpm (documentation) | Node >=22 / pnpm 12.3.4 | Documentation toolchain | Local docs build verification used Node 22.21.1; managed separately from the frontend project |

## Where to Verify Versions

All paths below are relative to the repository root:

| Scope | Source |
| --- | --- |
| Direct frontend dependencies and tool requirements | frontend/package.json, frontend/apps/web-antdv-next/package.json |
| Frontend version ranges and resolved versions | frontend/pnpm-workspace.yaml and the corresponding importers in frontend/pnpm-lock.yaml |
| Backend declarations and version management | backend/pom.xml, the four module pom.xml files, and Maven dependency:tree |
| Maven Wrapper | backend/.mvn/wrapper/maven-wrapper.properties |
| Default MySQL / Redis images | backend/docker-compose.yml |
| Documentation project | docs/package.json, docs/pnpm-lock.yaml |

This check is based on source commit `bab816a` and dependency files in the current workspace. Update this page and check actual resolved versions again when upgrading. The tables list major technologies, not a complete dependency inventory or compatibility certification list.
