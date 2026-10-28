# 新增业务模块

本页介绍如何将独立业务领域接入现有四模块后端。所有路径相对于仓库根；下列目录是教学目标，仓库当前尚未包含它们。

## 1. 创建目录

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

`api` 放跨模块的 Java 契约，不是 HTTP 接口目录；HTTP Controller 与业务实现都放 `biz`。本章用“按 ID 查询启用公告摘要”展示内部契约，CRUD 的 HTTP DTO/VO 不放进 api。

## 2. 编写模块 POM

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

安全 starter 提供本模块使用的权限注解；公共核心已经引入 Web、参数校验和 MyBatis-Plus，沿用父工程的版本管理，不单独再引入一套 MyBatis starter。Lombok 的 provided 依赖不会通过 core 传递给本模块，所以 biz 自己声明 provided，注解处理器沿用父 POM 配置。

父工程版本以后改变时，以上 parent/version 一并与实际仓库对齐，不能长期保留教程的旧版本。

## 3. 注册聚合与运行依赖

在 `backend/pom.xml` 的现有 modules 后追加：

```xml
<module>admin-notice-api</module>
<module>admin-notice-biz</module>
```

在 `backend/admin-system-start/pom.xml` 的现有 dependencies 中追加：

```xml
<dependency>
  <groupId>com.maplehush</groupId>
  <artifactId>admin-notice-biz</artifactId>
  <version>${project.version}</version>
</dependency>
```

**只注册 modules 不会让新模块进入应用运行时。** 启动模块还必须依赖 biz；原有 admin-system-biz 仍然保留，它负责认证、菜单、权限和审计等系统能力。

新增模块不创建另一个 Spring Boot main，也不配置可执行 Jar 重打包。实际依赖方向为：

```text
admin-system-start → admin-system-biz（保留）
                   → admin-notice-biz → admin-notice-api
                                     → admin-base-core
```

业务模块之间优先依赖 api 契约，避免互相依赖对方 biz。不要让 base-core 反向引用 notice。

## 4. 定义内部契约

下面两个文件位于 `admin-notice-api/src/main/java/com/maplehush/admin/notice/api/`。

::: code-group

```java [NoticeSummary.java]
package com.maplehush.admin.notice.api;

/**
 * 内部公告摘要；不是 HTTP 出参。
 *
 * @param id 公告标识
 * @param title 公告标题
 */
public record NoticeSummary(Long id, String title) {}
```

```java [NoticeQuery.java]
package com.maplehush.admin.notice.api;

import java.util.Optional;

/**
 * 供其他受信任业务模块查询启用公告。
 */
public interface NoticeQuery {
    /**
     * 查询启用公告摘要。
     *
     * @param id 正整数公告标识
     * @return 公告不存在或停用时为空
     */
    Optional<NoticeSummary> findEnabled(Long id);
}
```

:::

其他模块需要该能力时依赖 admin-notice-api 并注入 NoticeQuery，不调用 Controller、不直接引用 NoticeMapper。内部查询不会自动带来 HTTP 访问权限；对外开放时仍需由接口校验授权。

## 5. 扫描与资源位置

- 启动类位于 `com.maplehush.admin`，新包放在该前缀下可参与 Spring 组件扫描；类仍须正确标注 `@Service`、`@RestController` 等。
- Mapper 使用 `@Mapper`，沿用当前 MyBatis-Plus starter 扫描机制；不另外新建重复分页插件。
- Flyway 示例统一放在 `admin-system-start/src/main/resources/db/migration/`，不因模块拆分而复制一套迁移目录。
- 页面白名单仍在现有 system-biz 的 `menu-components.txt`，不要在 notice-biz 创建同名文件造成类路径资源覆盖。

完成目录和依赖后，继续[数据库与接口](./database-api.md)。
