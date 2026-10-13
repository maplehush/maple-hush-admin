# T13 个人资料与 OSS 头像

**前置**：T10。**范围**：当前用户本人资料、本人改密和头像上传；不增加文件管理菜单。

## 实施

- [ ] 实现 `/api/user/profile`、`/api/user/password`、`/api/user/avatar`；本人资料首版只允许修改 `realName`（映射 `sys_user.real_name`）和 `introduction`，页面中的 `username/roles` 只读，管理员 `remark` 不作为本人资料。个人中心由当前用户输入旧密码和新密码；改密成功后撤销本人所有会话，前端退出并跳登录页，用户直接用刚设置的新密码重新登录，不走用户列表的临时密码重置流程。
- [ ] 阿里云 OSS 头像按[已提供的非敏感配置](../架构设计/Redis与环境配置.md#oss-头像配置)使用北京地域、`maple-hush` Bucket、公开访问域名和 `vben-admin-img/` 前缀；先核对该 Bucket 是否适合独立公共读头像用途。后端验证 JPEG/PNG/WebP 实际内容和 2 MiB 上限，在此前缀下生成不可预测 Key，上传后将长期 HTTPS URL 写入 `sys_user.avatar_url`，并直接返回前端。
- [ ] 替换头像只从受控域名/前缀下旧 URL 提取 Key 清理；数据库失败时清理新对象。OSS 凭证仅由后端受控环境读取，不接受客户端任意 URL 绑定；不得使用 `VITE_OSS_*` 向浏览器传递 AccessKey。
- [ ] 本人资料和头像更新成功后清理 Redis 当前用户快照；本人改密递增 `authVersion`、撤销全部会话并清理快照，旧缓存不得返回修改前资料或恢复登录态。

## 验收

- [ ] 测试本人权限、旧密码错误、改密后的旧会话失效、超限/伪装图片拒绝、头像 URL 匿名可读但不可写，以及替换/失败清理。
- [ ] 用隔离测试 Bucket 验证 SDK 连接、最小写/删权限、公开 URL 匿名可读、前端构建产物无 AccessKey；后端 `verify` 通过。无 OSS 测试凭证时明确标记外部验收未完成，不用生产 Bucket 代替测试。
