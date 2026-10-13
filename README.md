# Maple Hush Admin

基于 Vben `apps/web-antdv-next` 与 Spring Boot 的企业内部后台。当前按[T0～T21 任务索引](./文档/任务拆解/README.md)逐项搭建；T01 在工作区根目录建仓，直接迁入前后端工程并精简其他 UI 应用，后端仍是 Spring Initializr 空工程。

## 仓库目录

| 路径 | 用途 |
| --- | --- |
| [`frontend/`](./frontend/) | Vben workspace；目标应用 `apps/web-antdv-next` |
| [`backend/`](./backend/) | Java 21 / Spring Boot 4.1.1 原始工程；T02 才改为四模块 |
| [`文档/`](./文档/index.md) | 架构、规范、接口契约与任务卡 |

前端来源：官方 `vbenjs/vue-vben-admin`，T01 用户新提供的提交 `151d80f9e5fb63f2ed8a8c432e1adf2eba253271`；上游 `origin`、迁入过程和验证结果见[T0 记录](./文档/任务拆解/T0-前后端原始工程准备.md#本次执行记录)与[T01 记录](./文档/任务拆解/T01-建仓迁入工程与前端基线.md#本次执行记录)。后端来自 Spring Initializr 官方生成的 `com.maplehush:admin-system-start` 空工程。

## 当前开发入口

在 `frontend/` 使用仓库锁定的 pnpm：

```powershell
pnpm install --frozen-lockfile
pnpm dev:antdv-next
```

目标应用类型检查和构建命令见[前端工作指引](./frontend/AGENTS.md)。当前开发环境的 `.env.development` 启用上游 Nitro mock；它只用于原版前端基线，不代表本产品后端已接通。后端的数据库、Redis、认证和四模块结构在后续任务实现。

## 文档

- [总体架构](./文档/架构设计/总体架构.md)
- [后端项目结构](./文档/架构设计/后端项目结构.md)
- [开发规范](./文档/开发规范/后端开发规范.md)
- [前后端接口契约](./文档/接口契约/前后端接口契约.md)
- [任务索引](./文档/任务拆解/README.md)和[完整执行流程](./文档/任务拆解/项目搭建与Docker一键部署执行流程.md)

不要提交凭据或将演示账号用于产品部署。构建、数据库和部署状态以每张任务卡的实际执行记录为准。

