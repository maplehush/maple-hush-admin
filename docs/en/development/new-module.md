# Add a Business Module

This page explains how to integrate a separate business domain into the existing four-module backend. All paths are relative to the repository root. The directories below are teaching targets and do not currently exist in the repository.

## 1. Create directories

```text
backend/
├── admin-notice-api/
│   ├── pom.xml
│   └── src/main/java/com/maplehush/admin/notice/api/
│       ├── NoticeQuery.java
│       └── NoticeSummary.java
└── admin-notice-biz/
    ├── pom.xml
    └── src/main/java/com/maplehush/admin/notice/
        ├── controller/NoticeController.java
        ├── dto/NoticeSaveDTO.java
        ├── dto/NoticeQueryDTO.java
        ├── vo/NoticeVO.java
        ├── entity/Notice.java
        ├── mapper/NoticeMapper.java
        └── service/
            ├── INoticeService.java
            └── impl/NoticeServiceImpl.java
```

`api` contains Java contracts shared across modules, rather than HTTP endpoints. HTTP Controllers and business implementations both belong in `biz`. This chapter demonstrates an internal contract for fetching an enabled announcement summary by ID; HTTP DTOs/VOs for CRUD do not belong in api.

## 2. Write module POMs

::: code-group

```xml [admin-notice-api/pom.xml]
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.maplehush</groupId>
    <artifactId>maple-hush-admin</artifactId>
    <version>0.0.1-SNAPSHOT</version>
  </parent>
  <artifactId>admin-notice-api</artifactId>
</project>
```

```xml [admin-notice-biz/pom.xml]
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.maplehush</groupId>
    <artifactId>maple-hush-admin</artifactId>
    <version>0.0.1-SNAPSHOT</version>
  </parent>
  <artifactId>admin-notice-biz</artifactId>
  <dependencies>
    <dependency>
      <groupId>com.maplehush</groupId>
      <artifactId>admin-notice-api</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>com.maplehush</groupId>
      <artifactId>admin-base-core</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <scope>provided</scope>
    </dependency>
  </dependencies>
</project>
```

:::

The security starter supplies the permission annotations used by this module. The common core already includes Web, parameter validation, and MyBatis-Plus. Follow the parent project's version management without adding another MyBatis starter. Lombok's provided dependency is not inherited transitively through core, so biz declares its own provided dependency; annotation processing uses the parent POM configuration.

If the parent project version changes, update parent/version above to match the actual repository rather than retaining an outdated tutorial version.

## 3. Register aggregation and runtime dependencies

Append to the existing modules in `backend/pom.xml`:

```xml
<module>admin-notice-api</module>
<module>admin-notice-biz</module>
```

Append to the existing dependencies in `backend/admin-system-start/pom.xml`:

```xml
<dependency>
  <groupId>com.maplehush</groupId>
  <artifactId>admin-notice-biz</artifactId>
  <version>${project.version}</version>
</dependency>
```

**Registering modules alone does not put the new module on the application's runtime classpath.** The startup module must also depend on biz. Keep the existing admin-system-biz, which provides system capabilities such as authentication, menus, permissions, and auditing.

Do not create another Spring Boot main or configure executable Jar repackaging in the new modules. The dependency direction is:

```text
admin-system-start → admin-system-biz (retained)
                   → admin-notice-biz → admin-notice-api
                                     → admin-base-core
```

Business modules should depend on api contracts rather than each other's biz modules. Do not make base-core depend back on notice.

## 4. Define internal contracts

The following two files belong in `admin-notice-api/src/main/java/com/maplehush/admin/notice/api/`.

::: code-group

```java [NoticeSummary.java]
package com.maplehush.admin.notice.api;

/**
 * Internal announcement summary; not an HTTP response object.
 *
 * @param id announcement identifier
 * @param title announcement title
 */
public record NoticeSummary(Long id, String title) {}
```

```java [NoticeQuery.java]
package com.maplehush.admin.notice.api;

import java.util.Optional;

/**
 * Allows other trusted business modules to query enabled announcements.
 */
public interface NoticeQuery {
    /**
     * Fetches an enabled announcement summary.
     *
     * @param id positive integer announcement identifier
     * @return empty if the announcement does not exist or is disabled
     */
    Optional<NoticeSummary> findEnabled(Long id);
}
```

:::

Modules that need this capability depend on admin-notice-api and inject NoticeQuery, rather than calling the Controller or directly referencing NoticeMapper. An internal query does not automatically grant HTTP access; an exposed endpoint must still check authorization.

## 5. Scanning and resource locations

- The startup class is under `com.maplehush.admin`. Place new packages under this prefix for Spring component scanning; classes still need appropriate annotations such as `@Service` and `@RestController`.
- Annotate Mappers with `@Mapper` and use the current MyBatis-Plus starter scanning mechanism. Do not add duplicate pagination plugins.
- Keep the Flyway examples together in `admin-system-start/src/main/resources/db/migration/`. Splitting modules does not require duplicating the migration directory.
- The page allowlist remains in the existing system-biz `menu-components.txt`. Do not create a file with the same name in notice-biz, which could shadow the classpath resource.

After creating directories and dependencies, continue to [Database and APIs](./database-api.md).
