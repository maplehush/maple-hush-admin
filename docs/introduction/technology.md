---
pageClass: technology-page
---

# 技术栈

下表是 **2026-10-28** 核对的项目依赖快照。前端采用 `frontend/pnpm-lock.yaml` 的实际解析版本，后端采用 Maven 依赖树；版本不是对上游“最新版”的声明。

## 前端

目标应用为 `frontend/apps/web-antdv-next`。

| 技术 | 版本 | 用途 | 说明 |
| --- | --- | --- | --- |
| Vben Admin 目标应用 | 5.8.0 | 后台布局、权限与应用基础 | 目标应用 package.json 版本；前端根包仍为 5.7.0，不作为本项目发布版本 |
| Vue | 3.5.41 | 响应式界面与组件 | 目标应用锁定的实际版本 |
| Vue Router | 5.2.0 | 路由与页面导航 | 配合后端菜单生成可访问路由 |
| Pinia | 4.0.3 | 应用状态管理 | workspace 公共 stores 与目标应用使用 |
| antdv-next | 1.5.2 | UI 组件 | 当前目标应用采用的组件库 |
| Axios | 1.19.0 | HTTP 请求 | 由 `@vben/request` 封装，应用层接入统一响应与会话处理 |
| Vue I18n | 11.4.10 | 国际化 | 由 `@vben/locales` 封装 |
| VueUse | 14.4.0 | Vue 组合式工具 | 对应 `@vueuse/core` |
| Tailwind CSS | 4.3.3 | 样式工具 | 前端公共样式与布局 |
| VXE Table | 4.21.2 | 数据表格 | 通过公共插件与应用适配层使用 |
| ECharts | 6.1.0 | 图表 | 公共插件提供图表能力，不代表所有图表业务都已实现 |
| Day.js | 1.11.23 | 日期处理 | 目标应用依赖 |

版本范围与解析结果可能不同。例如 catalog 中 `antdv-next` 声明为 `^1.4.5`，锁文件实际解析为 `1.5.2`。重新安装应遵循仓库锁文件，不能只按本文数字手工重装依赖。

## 后端

后端使用 Java 21、Spring MVC 和关系型数据库，最终交付一个可执行 Jar。

| 技术 | 版本 | 用途 | 说明 |
| --- | --- | --- | --- |
| Java | 21 | 编译与运行 | 父 POM 的 java.version / compiler release |
| Spring Boot | 4.1.1 | 应用装配与自动配置 | 后端父工程锁定 |
| Spring Framework | 7.0.9 | Web MVC、依赖注入、事务 | Spring Boot 管理；依赖树实际解析值 |
| Spring Security | 7.1.1 | 认证与接口授权 | Spring Boot 管理 |
| MyBatis-Plus | 3.5.17 | 数据访问与分页 | 使用 Boot 4 starter 和 JSQLParser 扩展 |
| MyBatis | 3.5.19 | SQL 映射 | 由 MyBatis-Plus 引入 |
| Spring Data Redis | 4.1.1 | Redis 访问 | Spring Boot 管理 |
| Lettuce | 7.5.2.RELEASE | Redis 客户端 | Spring Boot 管理；不同于 Redis 服务端版本 |
| Flyway | 12.4.0 | 数据库版本迁移 | core 与 mysql 扩展，由 Spring Boot 管理 |
| MySQL Connector/J | 9.7.0 | JDBC 驱动 | Spring Boot 管理；不同于 MySQL 服务端版本 |
| HikariCP | 7.0.2 | 数据库连接池 | Spring Boot 管理 |
| Hibernate Validator | 9.1.3.Final | 请求参数校验 | Spring Boot validation starter 引入 |
| Nimbus JOSE + JWT | 10.9.1 | JWT 编解码与校验底层 | 由 Spring Security OAuth2 JOSE 引入 |
| Lombok | 1.18.48 | 编译期样板代码生成 | 项目显式配置版本和注解处理器 |
| Hutool | 5.8.48 | 通用工具与图形验证码 | 使用 hutool-core、hutool-captcha |
| Knife4j Next | 5.7.9 | 在线接口文档 | Boot 4 starter；仅开发/测试环境开放 |
| springdoc-openapi | 3.0.3 | OpenAPI 生成 | 由 Knife4j starter 引入 |
| Swagger Jakarta 注解 | 2.2.55 | 接口描述注解 | 项目显式配置 |
| 阿里云 OSS SDK V2 | 0.6.0 | 对象存储访问 | 对应 alibabacloud-oss-v2，版本由模块 POM 指定 |
| TwelveMonkeys WebP | 3.15.3 | WebP 图片读取支持 | 用于图片处理与校验 |

Spring Boot 4 的应用 JSON 栈与部分依赖的 JSON 栈并存：依赖树包含 Jackson 3 `tools.jackson` **3.1.5**，也包含 OpenAPI / OSS 相关的 Jackson 2 `com.fasterxml.jackson` **2.21.5**（databind）。两者坐标不同，不能概括为一个统一的 Jackson 版本。

## 数据存储与外部服务

| 技术 | 版本 | 用途 | 说明 |
| --- | --- | --- | --- |
| MySQL | 8.4 LTS | 用户、角色、菜单、字典、日志与 refresh 会话等持久化数据 | Compose 默认镜像为 mysql:8.4；这是部署目标，实际补丁版本以运行镜像摘要为准 |
| Redis | 7.4 | 验证码、限流、用户快照与权限等缓存 | Compose 默认镜像为 redis:7.4-alpine；临时状态不替代 MySQL 持久化数据 |
| 阿里云 OSS | 不适用 | 头像及图片对象存储 | 托管服务没有对应的项目依赖版本；客户端 SDK 版本见后端表 |

历史本地开发验证曾使用 MySQL **8.0.24** 和 Redis **3.0.504**，不作为新的部署推荐版本，也不表示已经在所有版本组合完成认证、迁移与回归验收。

## 开发、构建与文档工具

| 工具 | 版本 | 用途 | 说明 |
| --- | --- | --- | --- |
| Node.js（前端） | ^22.18.0 或 ^24.12.0 | 前端工具链 | engines 允许范围；.node-version / CI 固定为 24.16.0 |
| pnpm（前端） | 11.16.0 | 前端 workspace 依赖管理 | packageManager 固定版本；不要直接套用 docs 的 pnpm 版本 |
| Vite（前端） | 8.3.0 | 开发与构建 | 前端锁文件解析值 |
| TypeScript（前端） | 6.0.3 | 类型系统 | 前端锁文件解析值 |
| vue-tsc | 3.3.11 | Vue 类型检查 | 前端锁文件解析值 |
| Turborepo | 2.10.13 | workspace 任务编排 | 前端锁文件解析值 |
| ESLint / Oxfmt | 10.10.0 / 0.65.0 | 代码检查与格式化 | 前端锁文件解析值 |
| Vitest | 4.1.11 | 前端单元测试 | 前端锁文件解析值 |
| Maven | 3.9.9 | 后端构建 | Maven Wrapper distributionUrl 固定版本 |
| JUnit Jupiter | 6.0.3 | 后端测试 | Spring Boot 测试依赖管理 |
| Docker Compose | 至少 2.24.4 | 容器编排 | 根配置使用 include；最低要求不是所有版本均已实测的声明 |
| GitHub Actions | 不适用 | 仓库 CI | 现有 workflow 检查代码；没有统一产品版本号 |
| VitePress | 1.6.4 | 文档网站 | docs 独立工程固定的稳定版本 |
| Node.js / pnpm（文档） | Node >=22 / pnpm 12.3.4 | 文档工具链 | docs 本机构建验证为 Node 22.21.1；与前端工程分开管理 |

## 版本从哪里核对

以下路径均相对于仓库根目录：

| 范围 | 核对来源 |
| --- | --- |
| 前端直接依赖与工具要求 | frontend/package.json、frontend/apps/web-antdv-next/package.json |
| 前端版本范围及实际解析值 | frontend/pnpm-workspace.yaml、frontend/pnpm-lock.yaml 的对应 importer |
| 后端声明与版本管理 | backend/pom.xml、四个子模块的 pom.xml、Maven dependency:tree |
| Maven Wrapper | backend/.mvn/wrapper/maven-wrapper.properties |
| MySQL / Redis 默认镜像 | backend/docker-compose.yml |
| 文档工程 | docs/package.json、docs/pnpm-lock.yaml |

本次核对基于源码提交 `bab816a` 及当前工作区的依赖文件；后续升级应同步更新本页，并重新检查实际解析结果。表中只列主要技术，不是完整依赖清单或兼容性认证清单。
