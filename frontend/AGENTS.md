# 前端工作指引

本目录源自 Vben 官方 `vue-vben-admin` 提交 `151d80f9e5fb63f2ed8a8c432e1adf2eba253271`；原始来源与迁入记录见[ T0](../开发文档/任务拆解/T0-前后端原始工程准备.md#本次执行记录)及[ T01](../开发文档/任务拆解/T01-建仓迁入工程与前端基线.md#本次执行记录)。本平台唯一目标应用是 `apps/web-antdv-next`。`playground/` 暂留作系统页面移植参考，其余四个 UI 应用已在 T01 移除；`apps/backend-mock/` 仅保留参考，T14 已关闭目标应用 mock，不作为正式后端或部署产物。

- 使用 `package.json` 的 `packageManager` 和 `engines` 选 Node/pnpm；本机直接运行 `pnpm` 时会按仓库配置切到 11.16.0，当前命令为 `pnpm install --frozen-lockfile`、`pnpm dev:antdv-next`。不要在本机用 `corepack pnpm install`：其子脚本会落到全局 pnpm 12，导致安装失败。目标应用检查使用 `pnpm --filter @vben/web-antdv-next typecheck` 和 `pnpm --filter @vben/web-antdv-next build`，实际执行后记录结果。
- 保留完整 workspace 依赖关系，不能只复制应用子目录。修改 `pnpm-workspace.yaml`、锁文件或移除演示应用时按对应任务卡操作并验证目标应用。
- `src/api/request.ts` 负责响应拆包和 access 刷新，`src/api/core/` 是现有目标应用调用，`playground/src/api/system/` 仅供系统页面参考。新增/改动 API、路由、日期处理或权限码时同步更新[前后端接口契约](../开发文档/接口契约/前后端接口契约.md)。正式 API 只使用同源 `/api` 前缀；登录、刷新、退出与 CSRF 的最终行为以契约和对应任务卡为准。
- 前端不得保存数据库/Redis 密码、JWT 密钥或 OSS AccessKey，`VITE_` 变量只能包含可公开配置。不要把 demo 用户、滑块验证码、mock 权限、假改密提示当成已实现功能。

## 系统页面与组件约定

- 系统页面优先直接迁入 Playground 对应模块，保留 list.vue、data.ts、modules/ 及官方布局，再适配真实 API；现有 user/role/menu/dept 已移动到目标应用，不再维护重复页面源码。其他 Playground 演示区域继续保留。
- 优先使用 Vben Page、useVbenVxeGrid、useVbenForm、useVbenDrawer/useVbenModal、VbenTableAction、VbenDescriptions 等已有封装；字典和日志新增页面也按相同模式组织。不要另写一套 Antdv Table/Form/Modal 替换已有 Vben 管理页面。底层输入、标签等按现有适配器使用 AntdvNext。参考 https://doc.vben.pro/components/introduction.html 。
- 管理页面文案面向实际使用者，保留必要的操作/错误提示；时区、DTO、缓存凭据、权限码和后端校验等实现细节写入开发文档，不堆在常规表单标签或提示中。
