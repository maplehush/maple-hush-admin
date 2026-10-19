# Maple Hush Admin

基于 Vben `apps/web-antdv-next` 与 Spring Boot 的企业内部后台，已实现登录认证、动态权限、个人资料，以及用户、角色、菜单、部门、字典和日志管理。当前按[T0～T21 任务索引](./开发文档/任务拆解/README.md)逐项搭建，已完成 T0～T16；T17 本地回归通过，真实 OSS 验收由用户执行。T18 已交付后端镜像与独立 Compose 配置，本地 Jar/文件密钥及依赖故障验证通过；本机缺少 Docker 引擎，容器构建、卷持久化、镜像检查与公网 TLS 验收待补。T19 已交付前端镜像与独立 Compose 配置，目标构建、原生 Nginx 与浏览器匿名冒烟通过；容器及公网 HTTPS 完整认证验收待补。T20～T21 未实施。

仓库地址为 `https://github.com/maplehush/maple-hush-admin.git`。`main` 为主分支，日常开发使用 `develop` 分支。

## 仓库目录

| 路径 | 用途 |
| --- | --- |
| [`frontend/`](./frontend/) | Vben workspace；目标应用 `apps/web-antdv-next` |
| [`backend/`](./backend/) | Java 21 / Spring Boot 4.1.1 四模块后端，接入 MySQL、Redis 和 Flyway |
| [`开发文档/`](./开发文档/index.md) | 架构、规范、接口契约与任务卡 |

前端来源：官方 `vbenjs/vue-vben-admin`，T01 用户新提供的提交 `151d80f9e5fb63f2ed8a8c432e1adf2eba253271`；上游 `origin`、迁入过程和验证结果见[T0 记录](./开发文档/任务拆解/T0-前后端原始工程准备.md#本次执行记录)与[T01 记录](./开发文档/任务拆解/T01-建仓迁入工程与前端基线.md#本次执行记录)。后端来自 Spring Initializr 官方生成的 `com.maplehush:admin-system-start` 空工程。

## 当前开发入口

在 `frontend/` 使用仓库锁定的 pnpm：

```powershell
pnpm install --frozen-lockfile
pnpm dev:antdv-next
```

前端启动、类型检查和构建命令见[前端说明](./frontend/README.md)。目标应用已关闭 Nitro mock，通过同源 `/api` 接入真实后端，本地联调需同时启动后端并准备 MySQL、Redis。真实 OSS 上传由用户后续测试，部署尚未完成。

## 文档

- [总体架构](./开发文档/架构设计/总体架构.md)
- [后端项目结构](./开发文档/架构设计/后端项目结构.md)
- [开发规范](./开发文档/开发规范/后端开发规范.md)
- [前后端接口契约](./开发文档/接口契约/前后端接口契约.md)
- [任务索引](./开发文档/任务拆解/README.md)和[完整执行流程](./开发文档/任务拆解/项目搭建与Docker一键部署执行流程.md)

不要提交凭据或将演示账号用于产品部署。构建、数据库和部署状态以每张任务卡的实际执行记录为准。

后端独立容器部署的配置、密钥初始化和验收步骤见 [后端部署说明](./backend/deploy/README.md)。当前本地打包与配置解析通过，Docker 容器实际验收待补，不能据此视为已上线。
