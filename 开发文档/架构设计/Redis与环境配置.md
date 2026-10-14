# Redis 与环境配置设计

> 2026-10-12 定稿；2026-10-13 核对 T02～T05 实现。T04 已实现验证码、限流和登录快照；T05 已实现轮换时重建、退出与重放时失效，T06 继续处理授权变化与当前用户读取。

## 首版职责

首版把 Redis 作为后端必需依赖，在 T02 接入 Spring Data Redis（默认 Lettuce）并完成连接和健康检查。MySQL 继续作为用户、授权关系及 `auth_refresh_session` 的持久化来源；refresh token 仍仅以哈希保存在 MySQL。JWT access token 保存身份、令牌族 ID 和 `authVersion`，不保存完整用户或权限列表。

Redis 使用带过期时间和前缀的键，承担三项职责：

| 数据 | 写入与读取 | 失效方式 |
| --- | --- | --- |
| 当前登录用户最小快照 | 正常登录成功后写入；受保护请求和 `/api/user/info` 可读取 `userId/username/realName/avatar/introduction/roles/authVersion` | 默认 10 分钟 TTL，可配置；资料更新、改密、重置、停用、退出时删除；版本不符直接重建 |
| 图形验证码 | 随机 `captchaId` 对应答案写入 Redis，2 分钟过期 | 登录校验时原子取出并删除，正确或错误均只能使用一次 |
| 验证码获取及登录限流 | 以可信客户端 IP、规范化账号的摘要等维度计数；计数和冷却时间由服务端控制 | 原子计数/脚本并设置 TTL，过期自动释放 |

缓存不得包含密码、密码哈希、refresh 明文/哈希、Cookie、完整 `SysUser` 或敏感请求体。键名不得直接暴露账号等个人信息；使用应用前缀和用途前缀。登录用户快照只是加速读取，不是会话或授权的最终依据。受保护请求仍要按现有架构验证 JWT、MySQL 中的账号状态与 `authVersion`、令牌族有效性；权限码和菜单由 MySQL 授权关系计算，同一请求内复用结果。Redis 快照缺失或过期时从 MySQL 重建。角色、菜单等授权变化先在 MySQL 提交并递增受影响用户的 `authVersion`，再清理缓存；即使清理失败，旧版本 JWT 也不能凭旧缓存继续授权。

正常登录须成功写入用户快照后才返回普通 access/refresh 凭据；Redis 写入失败时回滚初始会话事务，不发送凭据，避免遗留可用会话。待首次或重置后改密的登录只发专用短时凭据，不建立普通登录快照。刷新沿用 MySQL 的原子轮换，不把 refresh 会话搬到 Redis；刷新成功可延长或重建有效快照，但不得仅凭缓存发新 token。退出仅撤销当前 MySQL 令牌族并清理相关快照；其他已登录设备如共用快照，可在下一次请求从 MySQL 重建。本人改密和管理员重置目标用户撤销其全部会话、递增版本并清理快照。

T06 已实现当前用户缓存读取：先完成 MySQL 权威认证，再核对缓存的用户 ID 与 `authVersion`；缺失、无效 JSON、版本不符或 Redis 故障均从已验证的 MySQL 用户读取资料，尝试重建快照。响应角色使用本次 MySQL 查询结果，不以缓存角色作授权。`AUTH_USER_SNAPSHOT_TTL_SECONDS` 默认 600，允许 1～86400 秒；登录/刷新写缓存失败仍保持原有 503 与事务回滚策略。资料与授权提交后的清理方法由 `AuthorizationChangeService` 提供，真实写接口在后续任务接入。

T05 已将初始会话写入和 refresh 轮换置于 MySQL 事务内，Redis 快照写入失败时回滚会话写入/轮换并返回 503；客户端保留原 Cookie 重试。退出或重放撤销先提交 MySQL，再清理快照；缓存清理失败只记服务端错误，旧会话仍被数据库拒绝，退出继续清除浏览器 Cookie。`auth_refresh_session.auth_version` 保存签发版本，轮换时与用户当前版本比较；版本不符撤销该族并返回 401。

Redis 故障时，验证码签发/校验、登录及限流不可跳过，应返回可重试的服务错误；已经登录且正在处理的受保护请求可在 MySQL 权威校验后直接读取 MySQL，避免把缓存故障当作权限放行。后端进程重启不清空 Redis；Redis 进程重启是否保留未过期的验证码和限流计数取决于持久化配置，不能以“重启必失效”作为安全规则。Redis 数据确实清空时验证码和限流计数消失，已有合法登录快照按需重建；MySQL 中的 refresh 会话保持不变。liveness 只检测应用自身，不因 MySQL/Redis 故障触发进程反复重启；readiness 显式检查这两项首版必需依赖，故障时停止接收新流量。T04/T05/T06 和 T17 分别验证一次性消费、并发限流、缓存失效、数据清空与故障行为。

## 配置文件与连接位置

`admin-system-start/src/main/resources/` 已有四个文件：

| 文件 | 用途 | MySQL/Redis 主机 |
| --- | --- | --- |
| `application.yml` | 所有环境共享的非敏感配置；不硬编码激活环境 | 不写开发地址或密码 |
| `application-dev.yml` | 开发环境；IDE/Maven 宿主机启动或开发 Compose 启动共用 | 默认 `127.0.0.1`；Compose 注入 `mysql`、`redis` 服务名 |
| `application-test.yml` | 集成测试、共享测试及预发布环境 | 测试环境显式注入地址与凭据，不回退到开发或生产地址 |
| `application-prod.yml` | 生产部署 | 生产环境显式注入实际地址与凭据 |

显式使用 `SPRING_PROFILES_ACTIVE=dev|test|prod` 或等价启动参数选择环境，不在某个 profile 文件中设置 `spring.profiles.active`。`dev` 表示开发阶段，不区分宿主机和容器；连接地址由环境变量决定。`test` 使用独立测试数据和 Redis 命名空间，禁止连接生产服务。正式默认数据库名保持 `maple_hush_admin`，此默认值只写在 `dev` 示例中；`test` 和 `prod` 显式提供各自库名。用户给出的 `maple-hush` 仅为连接示例，不改正式库名。以下是 `application-dev.yml` 的开发环境示意；T18 核对部署密钥注入方式：

```yaml
spring:
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:127.0.0.1}:${MYSQL_PORT:3306}/${MYSQL_DATABASE:maple_hush_admin}?useUnicode=true&characterEncoding=utf8&connectionTimeZone=%2B08:00&forceConnectionTimeZoneToSession=true
    username: ${MYSQL_USERNAME:root}
    password: ${MYSQL_PASSWORD:123456}
  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}
      database: ${REDIS_DATABASE:0}
      timeout: 5s
      password: ${REDIS_PASSWORD:123456}
```

开发者在宿主机启动时直接使用 `dev` 的 `127.0.0.1` 默认地址；开发 Compose 启动时为后端注入 `MYSQL_HOST=mysql`、`REDIS_HOST=redis`，仍激活 `dev`。不要在宿主机直启时使用只能在 Compose 网络解析的服务名。按用户本轮决定，`dev` 的 MySQL 默认账号/密码为 `root/123456`，Redis 默认密码为 `123456`；环境变量可覆盖这些默认值。此例外只供本地开发，`.env.example` 也只展示开发值。`test` 和 `prod` 不配置开发地址或凭据兜底，地址、库名、Redis 数据库/键前缀与密码均由各环境显式提供；测试环境的数据和 Redis 键与开发、生产隔离。测试和生产使用各自独立的受控密码，不沿用开发密码。Redis 端口不对公网发布，开发 Compose 中若供宿主机访问，仅绑定回环地址。

`connectionTimeZone`（旧别名 `serverTimezone`）本身不保证 MySQL 会话时区为东八区，因此 T03 增加 `forceConnectionTimeZoneToSession=true`，使用 URL 编码的 `%2B08:00`。本机 MySQL 8.0.24 没有命名时区表，直接要求 `Asia/Shanghai` 会报 1298；固定偏移可连接。T03 的真实数据库集成测试确认 `@@session.time_zone` 为 `+08:00`，`CURRENT_TIMESTAMP` 与应用读写符合东八区秒级约定。目标 MySQL 8.4 环境仍需复验。

T04 开发配置的 `AUTH_JWT_SECRET` 可留空，此时每个进程启动生成新的随机签名密钥，重启会使旧 access 和改密凭据失效；`test/prod` 必须显式注入至少 32 字节的独立密钥。首次空库启动只在 `ADMIN_BOOTSTRAP_ENABLED=true` 时创建 `admin` 超管，创建后关闭该开关；已有唯一有效超管时开关不会再次建号。验证码获取按可信连接 IP 每分钟最多 10 次，登录按 IP 每分钟最多 20 次，账号连续密码失败 8 次后冷却 15 分钟；计数均存 Redis 并带 TTL，账号名在键中仅使用 SHA-256 摘要。这里的客户端 IP 取直接连接地址；部署反向代理时，T18 应明确可信代理边界和转发地址提取策略。

T05 新增 `AUTH_ALLOWED_ORIGINS`，生产/测试须显式配置逗号分隔的公开 Origin（仅 scheme/host/port，不含路径或通配符）。`dev` 默认包含目标 Vben 的端口 5999，并允许后端自身来源；登录、刷新、退出缺少有效 `Origin`/`Referer` 时返回 40301。CSRF 预期值保存在 `mh_csrf` HttpOnly Cookie，前端从 JSON 获取经 XOR 处理的提交值。会话清理配置见[总体架构](./总体架构.md)的会话规则与[T05 记录](../任务拆解/T05-会话刷新与退出.md#本次执行记录2026-10-13)。

## OSS 头像配置

用户提供的 OSS 非敏感参数作为首版头像存储的配置输入；T13 落地时核对 Bucket ACL、服务端上传和返回 URL 的实际可用性：

| 后端配置项 | 规划值 | 说明 |
| --- | --- | --- |
| `OSS_REGION` | `oss-cn-beijing` | Bucket 所在地域 |
| `OSS_BUCKET` | `maple-hush` | 头像 Bucket 候选；须确认适合独立公共读头像用途 |
| `OSS_PUBLIC_DOMAIN` | `https://maple-hush.oss-cn-beijing.aliyuncs.com` | 拼接头像长期访问 URL 的受控域名，不代替 SDK 连接配置 |
| `OSS_OBJECT_PREFIX` | `vben-admin-img/` | Object Key 基础前缀；头像对象在此前缀下生成不可预测的名称 |

`OSS_ACCESS_KEY_ID` 和 `OSS_ACCESS_KEY_SECRET` 只作为后端受控凭据变量名，本文、代码仓库、配置样例、日志和前端构建产物均不记录其值。本次对话出现过完整 AccessKey，后续部署须换用轮换后的凭据，不沿用已暴露的这一对。运行环境可用 RAM 角色时优先使用角色凭据；否则由后端从受控环境注入最小权限 RAM 凭据。前端不需要 `VITE_OSS_*`：头像上传走 `POST /api/user/avatar`，页面仅接收后端返回的 URL。`VITE_` 前缀变量会进入浏览器产物，不能存放 AccessKey。测试和生产凭据分开，T13 用隔离的测试 Bucket 验证；本次提供的地域、Bucket 和域名未经过实际 OSS 连接或 ACL 验证。

## 任务落点

- T02：四个配置文件、Redis starter/序列化与键前缀配置、连接与健康检查；验证 `dev` 在宿主机的实际连接及环境变量覆盖，检查 `test`/`prod` 缺少必需变量时不会落回开发地址。开发 Compose 的实际启动由 T18 验证。
- T04：Redis 验证码、原子消费、验证码/登录限流，以及正常登录的最小用户快照。
- T05：MySQL refresh 会话的轮换/撤销与 Redis 快照失效协同。
- T06：`/api/user/info` 使用快照读路径，账号和授权状态仍经 MySQL 校验；授权变化后按 `authVersion` 失效。
- T07：菜单增改删已在同一业务事务递增所有已分配角色用户版本，提交后删除快照；包括操作者的旧令牌失效，缓存清理失败不会恢复旧授权。
- T09：角色权限/状态变更及删除已定向递增原持有用户版本，提交后清快照；清理故障注入验证旧缓存不能恢复旧令牌授权，属性编辑不改变最小快照中的稳定角色编码。
- T17：真实 MySQL/Redis 集成测试激活 `test`，使用隔离的测试库和 Redis 键前缀；预发布也用 `test`，生产才用 `prod`。
- T18/T20/T21：后端 Compose 增加 `redis` 服务；根编排共 `frontend/backend/mysql/redis` 四服务，开发部署激活 `dev` 并注入 Compose 服务名，预发布激活 `test`，生产激活 `prod`。验证依赖健康、重启与缓存重建。Redis 是可重建的缓存，持久业务数据仍以 MySQL 备份为准。

参考：[Spring Boot Redis 配置](https://docs.spring.io/spring-boot/reference/data/nosql.html)、[Spring Boot Profiles](https://docs.spring.io/spring-boot/reference/features/profiles.html)、[Docker Compose 服务发现](https://docs.docker.com/compose/how-tos/networking/)、[Connector/J 时区属性](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-datetime-types-processing.html)。

## T12 日志保留配置

共享 application.yml 已增加 `maple-hush.log`，不依赖 Redis 保存审计或清理状态。`LOG_LOGIN_RETENTION_DAYS` 默认 90、`LOG_OPERATION_RETENTION_DAYS` 默认 180，均校验 1～3650 天；`LOG_CLEANUP_ENABLED` 默认 true，`LOG_CLEANUP_DELAY_MS` 默认 3600000，设为正的调度间隔毫秒数。各环境可覆盖这些非敏感配置；关闭清理不影响日志写入或查询。严格截止点、每批上限和故障处理见[总体架构的 T12 保留策略](./总体架构.md#t12-日志查询与保留清理)。审计失败通过固定 ERROR 事件及进程内计数观察，部署告警接入在后续运维任务验证。

## T13 已落地的 OSS 与本人改密配置

共享 application.yml 已配置 `maple-hush.oss`：OSS_REGION 默认 oss-cn-beijing、OSS_BUCKET 默认 maple-hush、OSS_PUBLIC_DOMAIN 默认 https://maple-hush.oss-cn-beijing.aliyuncs.com、OSS_OBJECT_PREFIX 默认 vben-admin-img/。OSS_ACCESS_KEY_ID、OSS_ACCESS_KEY_SECRET 及可选 OSS_SECURITY_TOKEN 仅从后端环境读取，未配置时头像上传返回 503，其他功能仍可启动。SDK 地域去除 oss- 前缀后使用默认 HTTPS Endpoint；不接受前端指定地址或凭据。

multipart 单文件 2MB、整请求 3MB；图片解码后重编码 PNG、宽高和像素限制见[T13 契约](../接口契约/前后端接口契约.md#t13-本人资料改密与头像实施细节)。客户端原始文件名/MIME 不参与路径和内容类型裁定。上传需 Bucket public-read 与 GetBucketAcl/前缀 PutObject/DeleteObject 权限，不自动调整 ACL。按用户指令，真实上传/匿名可读不可写由用户后续验证。

Redis 新增命名空间 `{key-prefix}:password_change:{userId}`，使用既有原子计数与过期脚本，每 5 分钟最多 5 次进入本人改密 Service 的请求；Redis 故障不绕过。本人资料/头像提交后清快照且读时与 MySQL 资料核对；本人改密还递增授权版本、撤销全部会话。

### T13 统一文件上传凭据

Redis 使用 `{key-prefix}:file_upload:{uploadId}` 保存随机标识对应的上传人 ID 和服务端生成 URL，TTL 为 2 小时；只由后端签发和读取，管理表单不能提交任意 URL。凭据不消费以支持提交失败重试，同一上传人可在有效期内复用素材；过期需重新上传，但不会删除已经交付的通用素材。公开素材目录为 `vben-admin-img/files/{上传人ID}/{随机名}.png`，继承现有 OSS 配置；该目录不允许直接配置到期删除生命周期，避免已绑定业务头像失效。
