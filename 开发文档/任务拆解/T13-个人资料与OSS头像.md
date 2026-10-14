# T13 个人资料与 OSS 头像

**前置**：T10。**范围**：当前用户本人资料、本人改密和头像上传；不增加文件管理菜单。

## 实施

- [x] 实现 `/api/user/profile`、`/api/user/password`、`/api/user/avatar`；本人资料首版只允许修改 `realName`（映射 `sys_user.real_name`）和 `introduction`，页面中的 `username/roles` 只读，管理员 `remark` 不作为本人资料。个人中心由当前用户输入旧密码和新密码；改密成功后撤销本人所有会话，前端退出并跳登录页，用户直接用刚设置的新密码重新登录，不走用户列表的临时密码重置流程。
- [x] 阿里云 OSS 头像按[已提供的非敏感配置](../架构设计/Redis与环境配置.md#oss-头像配置)使用北京地域、`maple-hush` Bucket、公开访问域名和 `vben-admin-img/` 前缀；后端上传前检查 Bucket ACL 为 public-read；真实 Bucket 配置由用户后续上传测试。后端验证 JPEG/PNG/WebP 实际内容和 2 MiB 上限，在此前缀下生成不可预测 Key，上传后将长期 HTTPS URL 写入 `sys_user.avatar_url`，并直接返回前端。
- [x] 替换头像只从受控域名/前缀下旧 URL 提取 Key 清理；数据库失败时清理新对象。OSS 凭证仅由后端受控环境读取，不接受客户端任意 URL 绑定；不得使用 `VITE_OSS_*` 向浏览器传递 AccessKey。
- [x] 本人资料和头像更新成功后清理 Redis 当前用户快照；本人改密递增 `authVersion`、撤销全部会话并清理快照，旧缓存不得返回修改前资料或恢复登录态。

## 验收

- [x] 自动测试本人权限、旧密码错误、改密后全部旧会话失效、超限/伪装图片拒绝、替换/失败清理及 SDK ACL/对象边界；外部存储以测试替身验证。
- [x] 后端 clean verify 通过；检查现有前端 dist 的凭据变量标记/AccessKey ID 模式，无匹配。本轮未重新构建前端。
- [ ] 真实 OSS SDK 连接、最小写/删权限、公开 URL 匿名可读且不可写：用户明确指定默认 OSS 可用，真实上传由本人后续测试，本轮不作已经通过的声明，不阻断代码交付。

## 本轮实施安排（2026-10-14）

用户已明确：按已提供的北京地域、Bucket/域名和前缀开发，真实图片上传由用户后续自行验证。本轮不使用生产 Bucket 代替隔离测试，也不使用会话中曾暴露的 AccessKey；代码从后端环境变量读取凭据，真实 OSS 验收保持待验证。

1. 本人资料/改密/头像真实 HTTP/MySQL/Redis 测试先行，存储以测试替身隔离外部副作用。
2. 实现 Service 接口/impl、DTO/VO 和 Mapper 专用查询；本人身份只从认证上下文读取，拒绝未知 JSON/上传资料字段；写入时锁用户并复核版本/状态。
3. 本人资料只写 realName/introduction；改密校验旧密码和统一新密码策略，Redis 按用户限制每 5 分钟 5 次尝试，成功递增版本、撤销全部 refresh 并清快照。错误旧密码返回 400，不触发前端无效刷新。
4. OSS SDK V2 0.6.0 与 WebP ImageIO 3.15.3（本轮从 Maven Central 官方元数据核对）用于真实格式解码。头像最大 2 MiB、最多 4096×4096 且总像素不超过 1600 万；不信任扩展名/MIME，解码后统一重新编码 PNG 去除元数据，生成 vben-admin-img/{userId}/{随机UUID}.png。
5. OSS 上传前检查 Bucket 为 public-read（拒绝 public-read-write/private）；对象继承 Bucket ACL，不自动修改 ACL。旧对象只清理受控 HTTPS 域名、前缀、当前用户目录、随机名称下的对象。提交失败清理新对象、提交后清理旧对象；失败输出安全对象键和计数，供运维补偿。
6. 当前用户快照读取同时对比 MySQL 中资料字段，防止删除失败或并发旧快照回填后返回旧资料。专项/全量回归与独立审查后更新交接。

裁定：资料返回 userId/realName/introduction，头像返回 avatar；改密返回 null 并清除 refresh/CSRF Cookie，前端跳转在 T16 对接。保留当前模块化单体和分层，不增加文件管理功能。原有根 POM 和前端 preferences.ts 改动不属于本轮；SDK 封装按既定结构进入 common/storage，SDK 依赖放公共模块 POM，WebP 依赖放业务模块 POM；不改根 POM。

## 执行记录（2026-10-14）

- 已交付 PUT /api/user/profile、PUT /api/user/password、POST /api/user/avatar，所有身份由登录上下文确定。Service 接口/impl、DTO/VO、专用 Mapper 查询和事务/操作审计已接入；本人简介不覆盖管理员备注，未知字段拒绝。
- 本人改密校验旧密码、常见密码、至少 8 字符及 UTF-8 不超过 72 字节，按用户 Redis 限流，成功递增 authVersion、撤销全部 refresh、清快照及 Cookie，不设置临时密码。并发改密只允许一方使用旧凭据成功，其他用户不受影响。
- JPEG/PNG/WebP 按实际内容解码、验证尺寸后统一重新编码 PNG 并去除元数据；原始/结果均最大 2 MiB。头像在本人目录使用随机 UUID，事务外上传、事务内重新锁用户核验版本并写 URL；提交失败/身份改变清新对象，成功后清旧对象。SDK 与存储单测验证 ACL、域名和目录归属、PUT 超时及清理失败计数，无真实云端副作用。
- RED：新增接口专项初始 9/9 因缺失接口返回 404 失败，错误 0。第一次实现后 8 例通过、1 例为测试误算限流次数：短密码被 Bean Validation 提前拒绝，不进入服务计数；修正测试后补入真实 WebP、上传期间授权失效与并发改密，最终 12/12 通过。新增 common/storage 5 例、头像存储边界 3 例单元测试通过。
- 专项命令：设置 T13_IT_ENABLED=true，在 backend 执行 `.\mvnw.cmd -ntp verify -Dit.test=ProfileManagementIT -Dfailsafe.failIfNoSpecifiedTests=false`，最终 BUILD SUCCESS。全量设置 T04_IT_ENABLED～T13_IT_ENABLED=true、MYSQL_DATABASE=maple_hush_admin，执行 `.\mvnw.cmd -ntp clean verify`，2026-10-14 15:06:52 完成，五个 reactor SUCCESS，33 个单元测试与 89 个真实本地集成测试通过；3 个 T03 迁移专项按开关跳过（Failsafe 合计 92，失败/错误 0）。
- 环境为 Java 21、本机 MySQL 8.0.24、Redis 3.0.504；T13 使用随机独立数据库/Redis 前缀，存储/SDK 使用测试替身。真实 OSS、MySQL 8.4、Docker 和浏览器个人中心本轮未运行；用户明确由本人后续验证实际上传，前端页面/跳转仍在 T16 对接。
- 新依赖固定为 OSS V2 0.6.0（admin-base-core）与 imageio-webp 3.15.3（admin-system-biz）。版本核对来自 [OSS Maven Central 元数据](https://repo.maven.apache.org/maven2/com/aliyun/alibabacloud-oss-v2/maven-metadata.xml)和 [WebP Maven Central 元数据](https://repo.maven.apache.org/maven2/com/twelvemonkeys/imageio/imageio-webp/maven-metadata.xml)，调用方式参照 [OSS 官方 SDK 示例](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/tree/main/samples/src/main/java/com/example/oss)。SDK 客户端延迟初始化，未配置凭据不影响其他功能启动；没有将会话中曾出现的 AccessKey 写入代码/配置/前端。
- 现有前端 dist 共 220 个文件，未发现 VITE_OSS_ACCESS_KEY_ID/VITE_OSS_ACCESS_KEY_SECRET 标记或 AccessKey ID 模式；这是既有产物检查，本轮未运行新的前端构建。WebP 测试素材取自 TwelveMonkeys 官方测试资源，并附上游许可和来源。
- 160 个 Java 源文件的大括号检查遗漏 0，git diff --check 通过；独立只读审查未发现需要修复的具体缺陷。已同步接口契约、架构/结构/配置、任务索引、交接和工作指引。用户原有根 POM 与前端 preferences.ts 改动保留，不纳入本次提交。
- 下一步 T14 前端登录与动态权限，由用户另行发起；T13 真实 OSS 上传由用户后续测试。
