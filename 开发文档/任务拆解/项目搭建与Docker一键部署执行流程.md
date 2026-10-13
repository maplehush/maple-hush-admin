# 项目搭建与 Docker 一键部署执行流程

> 状态：**逐项实施计划**。开发工作空间为 `D:\maple-hush-workspace`。T0 取得的 Spring Initializr 原始后端已移动到 `maple-hush-admin/backend/`；T01 使用用户重新提供的官方 Vben `151d80f9e5fb63f2ed8a8c432e1adf2eba253271` 源码，直接移动到 `maple-hush-admin/frontend/`。`参考代码/` 仅供比较。四模块工程和 Docker 配置尚待后续任务，未实际运行的验证命令不得写成已通过。

本文是从零搭建整套系统的总路线；每次交给 AI 执行的独立任务、依赖和验收见[任务拆解](./README.md)。后端的架构、接口和命名以[总体架构](../架构设计/总体架构.md)、[后端项目结构](../架构设计/后端项目结构.md)、[后端开发规范](../开发规范/后端开发规范.md)为准。目标应用是 Vben 的 `apps/web-antdv-next`；[Vben `d27a5b8a`](https://github.com/vbenjs/vue-vben-admin/tree/d27a5b8a0c2972a10d583a7050fcb889766ff83f) 只是编写本设计时核对的参考提交。T0 新拉取必须取执行时官方 GitHub `main` 的最新代码，已有工程则保留；实际版本确定后重新核对调用契约。同仓库 `playground` 只是演示代码，供移植系统页面时参考，不作为目标项目模块或部署产物。

## 0. 交付目标与边界

最终交付一个 Git 仓库、一个可执行后端 Jar、一套 Vben 前端静态文件。前端、后端各自可以部署；仓库根目录还可以一次启动 MySQL、Redis、Spring Boot、Nginx。四个后端 Maven 模块仍装配成**一个进程**，不增加微服务、MQ、代码生成或通用任务平台。首版功能为用户名/密码/图形验证码认证、用户、角色、菜单、部门、字典、登录日志、操作日志，以及基于阿里云 OSS 的本人头像上传能力。

仓库建议命名为 **`maple-hush-admin`**：连字符把品牌词分开，作为 Git 仓库和文件夹名更易读；Java 包名和 Maven `groupId` 仍保持 `com.maplehush`，不因为仓库名称改变而拆开。其下成对使用 `frontend/`、`backend/`，不再重复写 `admin-backend`；后端 Maven 模块仍沿用已确定的 `admin-*` 名称。

产品仓库直接放在开发工作空间根目录的 `maple-hush-admin/`，不增加中间目录层。架构设计、开发规范、接口契约、任务文档和参考资料统一放在仓库 `开发文档/` 中，与代码一起版本管理；工作区 `references/` 保存上游 Git 元数据，不作为项目构建输入。`开发文档/` 以后也作为 VitePress 文档站的源目录；`.editorconfig`、`AGENTS.md` 留在其生效的项目目录。

T0 已按原约定把原始工程放在工作空间根目录；T01 迁入后，根目录不再长期保留 `vue-vben-admin/`、`springboot-initializr/`。目标布局如下：

```text
<开发工作空间>/
├─ AGENTS.md                 # 当前工作区指引
├─ .editorconfig             # 后端工程的格式配置来源
├─ 参考代码/                  # 工作区外部参考，不作为产品源码
├─ maple-hush-admin/            # 唯一产品 Git 仓库
│  ├─ AGENTS.md              # 跨前后端指引
│  ├─ README.md              # 开发、配置、部署与文档入口
│  ├─ docker-compose.yml     # 前后端统一部署
│  ├─ .env.example
│  ├─ .gitignore
│  ├─ frontend/               # Vben workspace；目标应用 apps/web-antdv-next
│  │  ├─ AGENTS.md
│  │  ├─ docker-compose.yml   # 前端独立部署
│  │  ├─ .env.example
│  │  ├─ .dockerignore
│  │  ├─ apps/web-antdv-next/
│  │  ├─ internal/
│  │  ├─ packages/
│  │  ├─ scripts/deploy/
│  │  ├─ LICENSE
│  │  ├─ pnpm-lock.yaml
│  │  ├─ pnpm-workspace.yaml
│  │  └─ package.json
│  ├─ backend/                # Maven 聚合工程；四个初始模块
│  │  ├─ AGENTS.md
│  │  ├─ .editorconfig
│  │  ├─ pom.xml
│  │  ├─ Dockerfile
│  │  ├─ docker-compose.yml   # MySQL + Redis + 后端独立部署
│  │  ├─ .env.example
│  │  ├─ .dockerignore
│  │  ├─ admin-base-core/
│  │  ├─ admin-system-api/
│  │  ├─ admin-system-biz/
│  │  └─ admin-system-start/
│  └─ 开发文档/                # 唯一开发文档目录；未来 VitePress 站点根目录
│     ├─ index.md             # 文档导航首页
│     ├─ 架构设计/              # 总体架构、后端结构、数据库表结构
│     ├─ 开发规范/              # 后端开发规范
│     ├─ 接口契约/              # 前后端接口契约
│     ├─ 任务拆解/              # 总流程、任务索引、T0～T21、SQL 草案
│     └─ 参考资料/              # 手册和参考源码
└─ references/                  # 可选：不进入产品仓库的外部手册和研究材料
```

仓库 `README.md` 链接 `开发文档/` 的首页、架构、规范、接口契约和任务索引。工作区 `参考代码/` 与 `references/` 不作为产品源码或构建输入。

后续搭建文档站时，仓库的 `开发文档/` 使用站点首页 `index.md` 和 VitePress 配置目录 `.vitepress/`，以 `开发文档` 为构建源目录；`.vitepress` 是 VitePress 约定的专用目录名。若任务卡、SQL 草案和参考源码只供内部执行，不希望出现在发布站点中，应在建站任务中配置源文件排除。Vben 上游的 `docs/.vitepress` 可作配置参考，但须按本项目目录、导航和实际前端版本适配。

当前布局保留目标应用、`internal/`、`packages/`、`scripts/` 及 workspace 清单。`web-antdv-next` 依赖其他 workspace 包，因此必须整体移动官方仓库，不能只复制一个应用目录。T01 已依照[官方精简指南](https://doc.vben.pro/guide/introduction/thin.html)删除四个其他 UI 应用及其根脚本，并更新锁文件；`apps/backend-mock/` 暂供开发基线使用。用户明确要求 `playground/` 保留到整个系统开发完成，届时再删除并重新验证构建。正式前端部署只打包 `web-antdv-next`。保留上游许可证与固定提交记录，不把设计目录的参考源码再嵌套进产品仓库。

仓库根、`frontend/`、`backend/` 三个层级都有 `AGENTS.md`：根目录写项目地图和跨端契约，前端写 Vben workspace、pnpm、路由和 API 约束，后端写 Java、Maven 和配置规范。后端目录保存 `.editorconfig`；设计文档在仓库 `开发文档/` 中统一维护。

`Dockerfile` 负责构建单个服务的镜像；`docker-compose.yml` 负责把镜像、容器、网络、卷和启动关系编排起来，两者职责不同。Docker 官方现在优先称 `compose.yaml`，同时仍支持 `docker-compose.yml`；为与本项目参考的 [JeecgBoot 根目录文件](https://github.com/jeecgboot/JeecgBoot/blob/main/docker-compose.yml)及团队习惯一致，本项目统一使用 **`docker-compose.yml`**，不同时保留两种文件名。[Docker Compose 文件名说明](https://docs.docker.com/compose/intro/compose-application-model/)。

`backend/docker-compose.yml` 管后端、MySQL 与 Redis，也可在开发时只启动 MySQL 和 Redis；`frontend/docker-compose.yml` 管静态站点，使用可配置的后端 upstream；根 `docker-compose.yml` 通过 Compose `include` 组合两者并设置内部 upstream，避免复制一整套服务定义。要求 Docker Compose **2.20.0+**，实施时用 `docker compose config` 检查合并后的服务、变量和端口。[Docker Compose include 说明](https://docs.docker.com/reference/compose-file/include/)。前端独立部署仍需指向一个已可访问的后端 API；后端独立部署需要为公开 API 准备 TLS 入口。根部署时只由前端网关公开 HTTPS，MySQL 和 Redis 不公开。

子项目 Compose 不写固定 `container_name` 或相同的顶层项目名，让独立部署与根目录组合部署各自获得独立的容器/卷命名空间；端口与域名通过环境变量配置。实施时分别检查三种模式的 `docker compose config` 输出。

### 已下载 Vben 部署脚本的结论

已检查固定提交中的 [`scripts/deploy/`](https://github.com/vbenjs/vue-vben-admin/tree/d27a5b8a0c2972a10d583a7050fcb889766ff83f/scripts/deploy) 和 [部署工作流](https://github.com/vbenjs/vue-vben-admin/blob/d27a5b8a0c2972a10d583a7050fcb889766ff83f/.github/workflows/deploy.yml)：

| 文件 | 当前行为 | 本项目处理 |
| --- | --- | --- |
| `build-local-docker-image.sh` | 停止并删除同名容器与镜像，执行未锁定的 `pnpm install`，再构建镜像，最后只打印 `docker run` 示例；依赖 Bash | **在原脚本上改**构建目标和镜像名，依赖安装改为锁文件模式；删除“先删旧镜像”的步骤以便回滚；保留它作为手工构建/前端独立部署辅助入口，统一部署由 Compose 调用同一 Dockerfile |
| `Dockerfile` | Node 构建后用 Nginx 运行；执行面向几乎整个 monorepo 的构建，最终复制 `playground/dist` | **直接改这份 Dockerfile**，构建 `@vben/web-antdv-next` 及依赖，复制 `apps/web-antdv-next/dist`；确认产物路径，不另写一份前端 Dockerfile |
| `nginx.conf` | 8080 提供演示静态文件和 SPA fallback；没有 `/api` 反向代理；静态页面配置了宽泛 CORS 头 | **直接改这份 Nginx 配置**，保留 SPA fallback，增加可配置 `/api` upstream 与 HTTPS，去掉无关的静态 CORS 规则 |
| `.github/workflows/deploy.yml` | 按上游仓库条件通过 FTP 部署 `playground`、docs 及其他 UI 应用；没有部署 `web-antdv-next` | 不直接复用；将来如需 CI/CD，单独编写只面向目标应用的流水线 |

结论：部署时改造上游 `scripts/deploy` 中的文件，把构建产物由 `playground/dist` 切到 `apps/web-antdv-next/dist`。开发以 `apps/web-antdv-next` 为唯一目标，从 `playground` 借鉴系统模块代码；用户要求 `playground/` 保留至整个系统开发完成，之后再删除。前端独立和根目录统一部署都复用改造后的 Dockerfile。

**“一键部署”的准确含义**：在服务器已安装 Docker Compose、已放好 `.env`/密钥/证书、已开放域名和端口后，在仓库根目录执行 `docker compose up -d --build` 即可启动整套项目；在 `frontend/` 或 `backend/` 目录执行同一条命令则启动对应子项目。根目录还可在命令末尾指定 `frontend` 或 `backend`，只部署该侧及其声明的依赖。域名、TLS 证书、首次引导开关和数据备份属于首次部署前置准备。

**T0 原始工程准备**：用户在开发工作空间明确通知开始后，先按实际内容检查是否已有前后端工程；有则复用，不覆盖、更新或重建。前端缺失时，从 [Vben 官方 GitHub 仓库](https://github.com/vbenjs/vue-vben-admin)取得执行时 `main` 的最新代码，暂存于工作空间根目录 `vue-vben-admin/`；后端缺失时，从 [Spring Initializr 官网](https://start.spring.io/)生成 Java 21、Maven、Jar 的最小 Spring Boot 空工程，暂存于 `springboot-initializr/`。两套工程分别判断，记录准确路径、来源与版本。T0 不创建 `maple-hush-admin/`，不做系统功能或多模块改造；验收细节见 [T0 任务卡](./T0-前后端原始工程准备.md)。

## 1. 锁定基线和开发环境

1. T01 在开发工作空间根目录建立 `maple-hush-admin/` 和项目自己的 Git；T0 后端直接移动为 `backend/`，用户在 T01 重新提供的 Vben 官方完整仓库直接移动为 `frontend/`，避免嵌套上游 `.git`。按上述映射分类迁入设计文档。以新 Vben 提交重新核对 API、路由和部署脚本并更新设计，**从一开始就在 `apps/web-antdv-next` 开发**。T01 移除其他四个 UI 应用并更新根脚本/锁文件；`playground/` 在整个系统开发完成前留作演示参考，之后再删除并验证构建。
2. 安装 Java 21、Git、Docker Engine/Compose 2.20.0+；后端保留 Spring Initializr 生成的 Maven Wrapper。前端 Node/pnpm 版本以 T0 确认的实际 `package.json` 和锁文件为准；本设计参考提交当时要求 Node `^22.18.0 || ^24.12.0`、pnpm `11.16.0`，不得直接套用到新拉取的代码。IDEA 分别导入 `backend/pom.xml` 和 `frontend/`，Maven Runner JDK 设为 21。
3. 在 `frontend/` 执行 `pnpm install --frozen-lockfile`、`pnpm dev:antdv-next`，先确认未经改造的目标应用可启动。记录实际运行端口、构建产物目录和锁文件版本。
4. 决定首版数据库 ID 类型、密码哈希算法、JWT 签名密钥加载方式、Cookie 域与生产域名；按架构设计落实东八区 `DATETIME` 秒级字段和接口时间格式，并验证应用与 MySQL 会话时区。这些选择进入后端配置和首版迁移，不留给各功能自行决定。

**完成标志**：前端基线可运行；版本与约定记录在仓库 README。Spring Boot 4.1.x 与 MyBatis-Plus Boot 4 starter、OpenAPI 组件的版本组合此时列为兼容性验证项，不能只因 Maven 能解析就认为可用。Java 21 满足 [Spring Boot 4.1 的运行要求](https://docs.spring.io/spring-boot/system-requirements.html)。

## 2. 建立后端工程骨架

1. 以 T0 从 [Spring Initializr 官网](https://start.spring.io/)取得的**最小空工程**为起点：Maven、Java、Jar、Java 21、执行时可用的 Spring Boot 4.1.x 正式版，`groupId=com.maplehush`，初始 `artifactId=admin-system-start`、包名 `com.maplehush.admin`。不选 SNAPSHOT/Milestone，不用第三方生成的现成后台工程。[Spring Initializr 官方说明](https://docs.spring.io/initializr/docs/current/reference/html/)确认它可按构建系统、语言、坐标、Boot 版本和依赖生成工程。
2. 将原始工程的启动类、资源和 Maven Wrapper 作为 `backend/admin-system-start` 的种子；在 `backend/pom.xml` 建聚合父工程，补建 `admin-base-core`、`admin-system-api`、`admin-system-biz`，使总数恰好四个模块。父 POM 集中管理 Java、Spring Boot、MyBatis-Plus、MySQL Driver、Spring Data Redis、Flyway、测试与插件版本。把生成工程的 POM 改成子模块 POM，检查 wrapper 在后端根目录可用；不把 Initializr 的单模块布局硬套到最终多模块工程。
3. 保持依赖方向：`system-biz` → `base-core`、`system-api`；`system-start` → `system-biz`。只有 `system-start` 有 `AdminApplication` 并打包可执行 Jar。Service 仍按功能分包，使用 Controller → Service → Mapper，不为了形式创建 `I...Service`/`Impl` 成对文件。
4. 添加 `.gitignore`、后端 `.editorconfig`、`application.yml`、`application-dev.yml`、`application-test.yml` 与 `application-prod.yml`。`dev` 统一用于本机和开发 Compose，通过环境变量覆盖 MySQL/Redis 地址；`test` 使用独立测试数据与 Redis 命名空间。配置文件只保留非敏感默认值；数据库/Redis 密码、JWT 密钥和 OSS 凭证通过运行环境注入。开发环境 MySQL 和 Redis 的实际密码均为 `123456`，不写入提交的 YAML 或样例。`ADMIN_BOOTSTRAP_ENABLED` 默认关闭，首次空库初始化时由部署者显式开启。配置 OSS 区域、Endpoint、独立的公共读头像 Bucket、头像 HTTPS 公开访问域名、Object Key 前缀和上传上限；先准备隔离的测试 Bucket，生产再使用受控 Bucket。写入、删除权限只授给后端。
5. 先做最薄的启动链：Spring Web MVC、Security、Actuator、MyBatis-Plus、Flyway、MySQL 和 Spring Data Redis。建立健康端点，并通过本地 MySQL、Redis 启动应用；此阶段验证应用装配、统一响应和 JSON 基础序列化。分页插件、事务及时间字段填充在 T03 建表和最小 Mapper 落地后用真实 MySQL 验证。

**完成标志**：四模块依赖无环，只有一个 Jar；连接真实 MySQL、Redis 后空库启动并能读取健康状态。若 Boot 4.1 与任一 starter 存在兼容问题，先确定兼容组合并同步更新设计文档，再开发业务。

## 3. 先建表、迁移和首次引导

1. 先准备默认名为 `maple_hush_admin` 的空数据库和受限账号：Compose 内的 MySQL 首次启动按配置创建；连接已有 MySQL 时由具备权限的操作者创建。后端数据源选择该库，Flyway 迁移不写死库名。以[第一份建库建表 SQL 草案](./T03-01-建库建表草案.sql)为设计输入，去掉其中的建库与选库语句后生成 Flyway V1，创建 `sys_user`（含可空的 `avatar_url` 和 `must_change_password`）、`sys_dept`、`sys_role`、`sys_user_role`、`sys_menu`、`sys_role_menu`、`sys_dict_type`、`sys_dict_item`、`auth_refresh_session`、`sys_login_log`、`sys_operation_log` 及必要的唯一约束和索引；后续只新增迁移，不改已发布脚本。
2. 以[第二份系统基础数据 SQL 草案](./T03-02-系统基础数据草案.sql)为设计输入生成 Flyway V2，种入 `super_admin` 角色、系统目录、七个页面和操作按钮节点，并建立 `sys_role_menu` 授权关系；页面节点带查询权限码，按钮节点带操作权限码。T03 按 T01 核对实际路由后定稿，尚待 T14 建立组件入口、T15 完成真实交互的系统页面，不提前宣称浏览器可用。迁移脚本不写用户或密码；T03 完成后 `sys_user` 仍为空。首次迁移后的 `AdminBootstrapRunner` 与空库拒绝启动规则由 T04 实现：仅在 `ADMIN_BOOTSTRAP_ENABLED=true` 时用临时密码 `12345678` 创建首个超级管理员，保存哈希、标记必须改密并授予内置角色；开关关闭且数据库无用户时启动失败。正式部署只由 Flyway 执行 V1/V2，不预先手工执行两份草案。
3. 实现唯一超管账号保护与内置角色授予拦截，并用真实 MySQL 覆盖并发操作。设计 refresh 会话过期/撤销清理任务与保留期；登录日志和操作日志分别落在 `sys_login_log`、`sys_operation_log`。
4. 验证全新数据库安装、第二次启动幂等、已有数据库升级迁移、唯一约束与实体字段映射。

**完成标志**：全新库仅在显式开启引导时创建第一个管理员；再次启动不重复创建；关闭引导后空库启动失败，迁移脚本和数据库不保存临时密码明文。

## 4. 实现公共底座、认证和授权

1. `admin-base-core` 落地 `Result<T>`/`PageResult<T>`、统一异常、Bean Validation、请求 `traceId`、JWT 校验、MyBatis-Plus 配置、操作日志注解等公共技术能力。系统表查询和业务权限码留在 `system-biz`。
2. T04 先实现 `GET /api/auth/captcha`、`POST /api/auth/login`、`PUT /api/auth/initial-password`：正常登录须同时签发初始 access token 与 refresh Cookie，待改密用户只收到短时效专用改密凭据，两种响应互斥。T05 再实现 `POST /api/auth/refresh` 的轮换和 `POST /api/auth/logout` 的撤销；T06 交付 `GET /api/user/info`、`GET /api/auth/codes`、`GET /api/menu/all`。各任务严格按 T01 基于真实前端版本形成的接口契约清单返回；普通响应为 `code/data/error/message`（成功时 `error=null`），刷新成功直接返回 token 字符串，用户 ID 对前端序列化为字符串。
3. 验证码答案在 Redis 中保存 2 分钟并一次性原子消费；验证码获取与登录均通过 Redis 限流。正常登录缓存最小当前用户信息，待改密分支不建立普通登录快照。access token 短期有效并携带令牌族 ID；受保护请求确认 MySQL 中的账号状态、授权版本和令牌族仍有效。refresh token 只在 MySQL 存哈希、每次原子轮换，退出撤销当前令牌族并使其 access 随即失效。旧 refresh 重复提交时拒绝并撤销令牌族；前端 T14 负责合并并发刷新和协调同源标签页。登录失败按账号与可信客户端 IP 限流，登录结果写登录日志。refresh Cookie 在生产使用 HttpOnly、Secure、SameSite=Lax；T05 提供 `GET /api/auth/csrf`，前端 T14 对刷新/退出附 `X-XSRF-TOKEN`，退出不依赖 access 仍有效；服务端通过 refresh Cookie 定位会话族，并校验来源与 CSRF 凭据，登录/退出后重新获取凭据。
4. 实现统一密码规则：新密码至少 8 个字符，拒绝 `12345678` 等常见密码和原密码；T04 的首个超管引导与 T10 的新建用户、用户列表管理员重置复用同一临时密码规则并标记必须改密。首次或用户列表重置后登录必须改密，改密完成后清理凭据并返回登录页；个人中心由当前用户输入旧密码和新密码，成功后撤销本人会话、清理前端登录态并返回登录页，再用新密码登录。以 `sys_user_role`、`sys_role_menu`、`sys_menu` 为唯一授权来源；权限码统一小写，页面节点可承载查询码，按钮节点承载操作码，角色授权须保留目录祖先及按钮所属页面。管理接口先默认需要登录，再以 `@PreAuthorize` 判定具体权限；Service 对角色分配和授权等高风险写操作二次核对范围。授权变化同步增加受影响用户 `authVersion`，旧 token 失效。T06 同时落地操作日志写入切面与事务边界，供 T07 起的管理写操作直接使用。

**完成标志**：无身份为 401，已登录但无权为 403；过期 access 可按前端约定刷新；旧 refresh 重放失败；直接访问无权限接口仍被阻止。

## 5. 实现系统管理功能

按依赖顺序完成 **菜单 → 部门 → 角色 → 用户 → 字典 → 日志查询**，同时补齐这些功能之间的授权和菜单关系。认证阶段先实现读用户、角色与菜单所需的最小查询；本阶段再补齐管理操作。每项都要交付 Controller、DTO/VO、Service、Mapper/实体、权限码、菜单节点、实际接入 T06 审计能力及有价值的测试。登录日志由认证 Service 写；T12 复核两类日志写入并完成查询、授权和保留清理。

| 功能 | 第一批应覆盖的动作与校验 |
| --- | --- |
| 部门 | 树查询、增改删、父子关系与循环校验、有关联用户时的删除规则 |
| 用户 | 分页/详情、增改/状态、分配角色、唯一超管账号保护；创建与用户列表重置设置临时密码 `12345678`、强制目标用户改密并撤销其旧会话；唯一超管只在个人中心改密 |
| 角色 | 列表/增改删、菜单授权、内置角色保护、可授予范围校验 |
| 菜单 | 管理树、名称/路径查重、路由与按钮类型、用户可见路由树组装 |
| 字典 | 类型/字典项管理与查询、同类型下字典值唯一 |
| 日志 | 登录与操作日志分别查询；摘要脱敏、分页及访问权限 |

这张表是**六个系统功能领域**，不是“总共六个页面”或“只保留六个路由”。建议首版左侧系统菜单至少有用户、角色、菜单、部门、字典、登录日志、操作日志 **七个实际页面**；日志属于一个功能领域，但登录日志与操作日志分开查询、分开授权。一级菜单可按“系统管理”（前五项）和“系统日志”（后两项）归类，或者根据实际导航空间合并为一个系统分组；不额外套入岗位管理、参数管理、代码生成等与当前 Vben 目标无关的页面。

此外，`web-antdv-next` 已有从头像下拉进入的“个人中心”隐藏路由和修改密码页，它不计入上述六个系统管理领域。当前演示页面的修改密码只是显示成功提示，基本资料里的角色选项也是 mock；正式交付时要接当前用户资料读取/修改、修改本人密码的真实接口，角色只能展示，不能由用户自行修改。本人头像通过 `POST /api/user/avatar` 上传到阿里云 OSS 独立的公共读头像 Bucket，`sys_user.avatar_url` 保存长期 HTTPS URL 并在资料中直接展示；这是一项用户能力，不增加文件管理菜单。其他无真实业务支撑的演示标签页应移除或禁用，不能保留虚假的成功反馈。现有首页若保留，也按目标应用的页面处理，不因此新增后台业务模块。

每完成一组 API，用 HTTP 测试与真实 MySQL 检查 DTO 校验、分页 `items/total`、唯一冲突、事务回滚、直接越权请求、审计结果。菜单路由的 `component` 在 T14 建立对应的最小页面入口，并在 T15 验证真实页面；按钮节点不进入路由树，权限码从获授且有效的页面与按钮节点产生。

## 6. 移植并联调 Vben 前端

1. 开发始终在目标 `frontend/apps/web-antdv-next` 进行。T14 先建立与已种入动态菜单匹配的最小页面组件入口，只验收登录、路由加载与权限响应，不显示虚假的业务数据或成功提示；T15 再从演示源码 `playground/src/views/system/{user,role,menu,dept}` 和 `playground/src/api/system` **借鉴并移植**，在同一路径逐页补齐组件、类型、分页体和字段，并新增字典与日志页面。先核对实际 UI 构件，不能仅复制路径。登录、当前用户和路由以目标应用现有 `src/api/core/auth.ts`、`src/router/access.ts` 为准。
2. 改造 `apps/web-antdv-next/src/views/_core/authentication/login.vue`：保留用户名、密码输入，彻底移除演示账号选择、自动填入密码和 `SliderCaptcha`；加入图形验证码输入框、图片和刷新入口。`src/api/core/auth.ts` 增加获取验证码调用、`captchaId/captchaCode` 类型以及正常登录/待改密两种互斥响应；`src/store/auth.ts` 先判断 `passwordChangeRequired`，待改密时不写入 `accessStore`，转入仅允许修改密码的独立页面，路由守卫允许该页面以专用凭据访问。改密成功后清理临时凭据并跳登录页，不先进入系统首页；登录失败或验证码过期后刷新图片。交互参考 JeecgBoot，保持 Vben 视觉样式。
3. 修改目标应用偏好：`accessMode: 'backend'`、`enableRefreshToken: true`。关闭开发 mock；开发环境仍以 `/api` 调用后端，将 Vite 代理指向 `http://localhost:8080`，**不要剥掉 `/api` 前缀**。
4. 将生产 `VITE_GLOB_API_URL` 从远程 mock 地址改为同源 `/api`；构建时检查产物没有远程 mock URL。T14 对照最小页面组件核对动态路由加载，T15 再逐页核对最终 `src/views/**/*.vue` 路径；避免同一路由既静态注册又动态下发。同页并发 401 只能触发一次 refresh，同源多标签页须协调刷新并共享新 access token，无法协调时要求重新登录。
5. 联调验证码获取、刷新、登录、首次/重置后强制改密、退出、401 自动刷新、用户/角色/菜单/部门/字典 CRUD、权限码控制按钮、无权限直调 API、动态菜单刷新与授权变更后的重新登录/令牌失效。
6. 接通头像下拉“个人中心”的本人资料、头像上传与改密接口，移除 mock 角色可编辑项和没有实际保存能力的演示设置；本人改密成功立即清理登录态并跳登录页。管理员重置别人密码后仍留在管理页，目标用户旧会话失效。验证本人不能通过资料接口改角色或账号状态，OSS AccessKey 不进入前端代码或产物。
7. 目标应用稳定后，删除目标仓库里的 `playground` 与其他不再使用的演示应用，更新 workspace/锁文件；重新执行类型检查、构建和完整浏览器联调，确保删掉演示代码后仍可独立交付。

**完成标志**：从浏览器登录后可完成全部首版系统管理流程，网络请求统一走 `/api`，没有 mock 依赖；菜单与接口权限一致。

## 7. 应用功能回归与镜像前门槛

1. 后端在 `backend/` 运行 `./mvnw.cmd verify`（Windows）或 `./mvnw verify`（Linux/macOS）；配置 Failsafe，使 `*IT` 在 `verify` 阶段运行。集成测试连接真实 MySQL 与 Redis，覆盖 Flyway、Mapper、事务、Boot/MyBatis-Plus/Redis 兼容性和关键 HTTP 契约。
2. 前端在 `frontend/` 运行 `pnpm --filter @vben/web-antdv-next typecheck` 和 `pnpm --filter @vben/web-antdv-next build`；再做至少一次浏览器端到端冒烟检查。
3. 关键安全场景必须验证：空库引导开关、唯一超管账号保护与内置角色并发授予拦截、越权授权拒绝、401/403、验证码过期与重复提交、验证码获取和登录限流、固定临时密码仅能进入改密流程、待改密凭据不能调用普通 API、首次/本人改密后重新登录、管理员重置仅踢出目标用户、refresh 轮换与重放、Cookie/CSRF、个人中心不能修改本人角色、改密后旧 access/refresh 失效、敏感字段不入日志；用测试 Bucket 验证头像上传限制、本人绑定、公开 URL 可长期读取、匿名不可写和失败清理。
4. 构建镜像前检查迁移的空库和升级路径、前端产物无远程 mock 地址；CI 应先执行可自动化的应用检查，再构建正式镜像。镜像是否含密钥、Compose/TLS 是否可用及数据库备份恢复须在 T18～T21 的部署形态下继续验收。

**完成标志**：应用功能检查有实际运行记录；失败项修复后重新验证。这是 T18/T19 的前置门槛，不代表镜像和最终部署已验收。

## 8. 制作镜像与 Compose 编排

1. `backend/Dockerfile` 从 `backend/` 构建四模块工程的唯一可执行 Jar，运行阶段使用 Java 21 和非 root 用户；不复制源码、测试报告或密钥。`backend/docker-compose.yml` 包含 MySQL、Redis 与后端，MySQL 数据放命名卷，Redis 缓存可重建且端口不对公网发布；默认可将后端端口只绑定宿主机回环地址。若后端要单独对公网提供 API，用可选的 API 网关/TLS 配置发布域名，不能直接把明文 8080 暴露公网。
2. **原位改造** `frontend/scripts/deploy/Dockerfile`、`nginx.conf` 和 `build-local-docker-image.sh`，以 `frontend/` 为构建上下文，锁定 Node/pnpm 版本，只构建 `@vben/web-antdv-next` 及其 workspace 依赖，运行镜像只含目标应用静态文件和 Nginx 配置。保留上游 SPA fallback；把 `/api` 代理改为可配置的后端 upstream，保持一个 `/api` 前缀。独立部署时 upstream 指向已发布的后端 HTTPS 域名；根目录统一部署时指向 Compose 内部的 `backend:8080`。生产 Web 使用同源 HTTPS 与 HTTP 跳转。
3. `frontend/docker-compose.yml` 定义名为 `frontend` 的服务，使用上述原有目录中的 Dockerfile 构建；`backend/docker-compose.yml` 定义 `mysql`、`redis` 与 `backend`；根 `docker-compose.yml` 用 `include` 组合两份文件，并由根 `.env` 覆盖 `BACKEND_UPSTREAM` 为内部地址。子项目各有 `.env.example`，根目录也有 `.env.example`；样例列出非秘密的 `COMPOSE_PROJECT_NAME`，供同机多套部署时分别设置。统一部署不得同时启用后端独立部署的公网 API 网关，以免和前端的 80/443 端口冲突。`docker compose config` 必须确认最终项目名、服务名、端口、卷和构建路径正确。
4. 每个服务设置健康检查、重启策略与日志限制；后端用 `depends_on: condition: service_healthy` 等待 MySQL 和 Redis，而不是只等容器进程启动。[Docker Compose 启动顺序](https://docs.docker.com/compose/how-tos/startup-order)。前端容器可先启动，但整套部署只有在后端就绪且 `/api` 冒烟请求成功后才算完成。
5. 后端提供 Actuator 健康端点，Compose 从容器内部探测。liveness 只代表应用自身存活，不检查 MySQL/Redis；readiness 显式检查两项首版必需依赖，避免服务尚不可用时接收新流量。Spring Boot 默认 readiness 指标不自动包含外部依赖，须明确配置与故障测试。[Spring Boot Actuator 健康端点](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html)。健康端点不对公网暴露详细内部信息。
6. `.env.example` 只列域名、端口、镜像标签、默认数据库名 `maple_hush_admin`/用户名、Redis 地址/端口、upstream、`ADMIN_BOOTSTRAP_USERNAME=admin`、`ADMIN_BOOTSTRAP_ENABLED=false` 和 OSS 地域/Bucket/公开域名/Object Key 前缀等非秘密项；已提供的 OSS 值见[环境配置设计](../架构设计/Redis与环境配置.md#oss-头像配置)。后端 Compose 须将数据库名同时用于 MySQL 首次建库配置和后端数据源，并将两个引导变量传给应用。数据库和 Redis 密码、JWT 密钥、OSS RAM 凭证（若环境不能使用 RAM 角色）及 TLS 私钥放在各项目被 `.gitignore` 排除的 `secrets/` 或宿主机受控路径。Compose secret 会以文件挂载给被授权服务，后端须**明确实现从文件读取**，不能假定 `*_FILE` 环境变量会被 Spring 自动解析。[Docker Compose secrets](https://docs.docker.com/compose/how-tos/use-secrets/)。前后端各自的 `.dockerignore` 排除本地 `.env`、`secrets/` 与构建产物。OSS 是外部服务，不在 Compose 中增加存储容器；部署主机需能通过 HTTPS 访问选定的 OSS Endpoint。
7. 分别演练三种模式：后端独立、前端连接外部已运行 API、根目录同时启动。再用新数据库和已有卷演练重建；确认 Flyway 由后端启动触发，不依赖手工执行 SQL。

**完成标志**：根目录模式运行目标前端、单个后端进程、MySQL 和 Redis；独立模式各自有明确启动入口；数据库卷持久，Redis 缓存可重建，前端同源 `/api` 可用，密钥未进入仓库和镜像。

## 9. 首次部署：默认命令即可

在服务器上完成一次性准备：安装 Docker Compose 2.20.0+、拉取项目、配置域名解析和 80/443 防火墙；按使用的入口复制对应 `.env.example` 为 `.env`；准备受控的数据库/Redis 密码、JWT 密钥、独立公共读头像 Bucket/最小权限写入凭证、头像公开访问域名和 TLS 证书文件，设置文件权限。首次空库部署前确认 `ADMIN_BOOTSTRAP_USERNAME`，将后端的 `ADMIN_BOOTSTRAP_ENABLED` 显式设为 `true`，引导创建的超级管理员临时密码为 `12345678`。首次登录被强制改密，改密后回登录页；完成后将开关改回 `false` 并重新应用配置。开关关闭而数据库意外变为空库时，启动明确失败。证书的申请/续期机制应在上线前选定并演练。

后端通过 `SPRING_PROFILES_ACTIVE` 显式选择环境：开发用 `dev`，测试和预发布用 `test`，生产用 `prod`。开发 Compose 为 `dev` 注入 `MYSQL_HOST=mysql` 与 `REDIS_HOST=redis`；测试和生产分别注入独立的数据库、Redis 地址、库名和密钥，不复用开发默认值。

**从仓库根目录操作**：同一份根 `docker-compose.yml` 使用 `frontend`、`backend`、`mysql`、`redis` 四个明确的服务名，支持三条简短命令：

```powershell
# 同时部署前端、后端、MySQL 和 Redis
docker compose up -d --build

# 只部署或更新前端
docker compose up -d --build frontend

# 只部署或更新后端及其 MySQL、Redis 依赖
docker compose up -d --build backend
```

**前后端项目也保留各自独立入口**：进入 `frontend/` 或 `backend/` 后同样执行一条命令；前端独立部署时，其 `.env` 中的 `BACKEND_UPSTREAM` 指向已经可访问的后端 HTTPS origin（不带 `/api`）。

```powershell
docker compose up -d --build
```

`docker-compose.yml` 在当前目录会被 Compose 自动发现，旁边的 `.env` 也会自动用于变量替换；只有配置文件放在别处或临时切换环境文件时才需要 `-f`、`--env-file`。[Docker Compose 文件发现](https://docs.docker.com/compose/intro/compose-application-model/)、[`.env` 默认加载规则](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/)。注意 `.env` 是配置变量文件，**不是**密钥保护机制；数据库/Redis 密码和 JWT 密钥仍按前文通过受控文件/Compose secrets 注入。

**同一台服务器部署两套时**，`backend`、`frontend`、`mysql`、`redis` 仍是各自 Compose 项目内的服务名，可以重复；同一份 Compose 配置内的服务名则必须唯一。分别在两套部署目录的根 `.env` 中设置不同的 `COMPOSE_PROJECT_NAME`（例如 `maple_admin_a`、`maple_admin_b`），两边仍各自执行 `docker compose up -d --build`；不要写固定 `container_name`、全局固定卷名或网络名。若显式配置 `image:`，镜像标签也应区分实例。Compose 默认按项目名隔离容器、网络和命名卷，`http://backend:8080` 在本项目的默认网络内解析。两套前端不能同时在同一宿主机 IP 上绑定 80/443；须分别配置不同宿主机端口/IP，或由一个统一入口按域名转发。若共用外部 MySQL、Redis 或 OSS，还要分别设置数据库/账号、Redis 键前缀/数据库和 OSS Object Key 前缀（或 Bucket），避免跨实例读写和清理。[Docker Compose 项目名](https://docs.docker.com/compose/how-tos/project-name/)、[默认网络与服务发现](https://docs.docker.com/compose/how-tos/networking/)。

后端服务依赖 `mysql` 和 `redis`，从根目录选择 `backend` 会确保两项依赖启动；`frontend` 不强制依赖后端，选择 `frontend` 不会顺带启动后端。只部署前端时，后端应已运行，或把根 `.env` 中的 upstream 指向外部已部署的 API；否则页面 `/api` 暂不可用。[Docker Compose 按服务启动](https://docs.docker.com/reference/cli/docker/compose/up/)。

根文件使用 `include` 装配子项目 Compose；后端完成 MySQL/Redis 健康等待、Flyway 迁移和首次引导，前端提供统一 HTTPS 入口。一起部署时根 `.env` 设 `BACKEND_UPSTREAM=http://backend:8080`。`build.context` 在各子项目 Compose 中相对于其自身目录解析；首次部署要按环境设置足够的健康检查启动宽限期。上线前在相应目录运行 `docker compose config`，核对变量、服务和端口。

接着逐项验收：

```powershell
docker compose ps
docker compose logs --tail=100 backend
```

根目录模式检查 `mysql`、`redis`、`backend`、`frontend` 均为 healthy；访问 HTTPS 首页；获取验证码后用初始管理员和临时密码登录，完成强制改密并重新登录，验证 `/api/user/info`、`/api/auth/codes`、`/api/menu/all` 和五个系统页面；上传一张测试头像并确认公开 URL 可读、匿名不可写；退出后确认 refresh 已撤销。对外部主机测试 MySQL、Redis 端口和后端明文 8080 未开放。清空 Redis 数据后验证码应失效、合法登录快照应按需重建；仅重启 Redis 时按持久化配置核对键是否保留。重启容器后再次登录，确认 MySQL 数据和日志仍在。独立模式还需验证前端指向外部 API 的代理与 Cookie 行为，以及后端独立网关的 TLS。这些是**将来执行时必须做的动作**，不是本文已经完成的结果。

## 10. 上线后的升级、备份与回滚

- **备份**：定时备份 MySQL 并做一次异机恢复演练；记录备份时间、数据库版本、迁移版本、应用 Git 提交和镜像标签。保存密钥的恢复方案，避免只有数据备份却无法验证旧 token 或解密配置。
- **升级**：先在预发布环境对生产数据副本验证 Flyway；备份生产库，再固定代码提交/镜像标签并运行同一条 Compose 命令。上线后检查健康状态、登录、菜单和关键写操作。
- **回滚**：应用镜像回退与数据库回退分开设计。Flyway 迁移默认向前执行，不能假设 `docker compose` 回退镜像就能撤销表结构；破坏性迁移须采用兼容发布、数据备份和明确的恢复步骤。回滚前判断旧应用是否兼容当前 schema。
- **日常运维**：查看 `docker compose ... ps/logs` 和 Actuator 健康、告警；清理过期 refresh 会话和日志按确定的留存期执行。停止服务时避免使用 `down -v`，它会删除命名卷中的数据库数据。Compose 的生产配置与更新建议见 [Docker 官方说明](https://docs.docker.com/compose/how-tos/production/)。

## 最终交付检查表

- [ ] Vben 目标应用与系统页面完整，登录页使用服务端图形验证码、无滑块和演示账号自动填充；首次/重置后强制改密，本人改密后返回登录页；开发和生产均无 mock 调用，`/api` 只出现一次。
- [ ] 四模块后端打出一个 Jar，系统 API、权限、审计、迁移和首版功能可用。
- [ ] 真实 MySQL/Redis 集成测试、前端类型检查/构建和浏览器冒烟检查有运行记录。
- [ ] 首次引导需显式开启，超级管理员、新建用户和重置用户的临时密码均为 `12345678` 且首次必须改密；正式密码、JWT/数据库密钥和 OSS AccessKey 不在 Git、镜像、日志或前端产物里；本人头像可上传到独立的公共读 OSS Bucket，数据库保存长期 HTTPS URL。
- [ ] `frontend/`、`backend/` 各自可用 `docker compose up -d --build` 部署；仓库根目录同一命令可部署全套，追加 `frontend` 或 `backend` 可单独部署或更新一侧。
- [ ] HTTPS、健康检查、数据持久化、备份恢复、升级和回滚演练完成。
