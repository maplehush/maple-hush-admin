# 前端规范

当前唯一目标应用是 `frontend/apps/web-antdv-next`。保留整个 frontend workspace 安装依赖，不单独复制应用目录，也不把 `backend-mock` 当作真实服务。

## 目录与命名

| 位置（相对目标应用） | 内容 |
| --- | --- |
| `src/api/` | 请求参数、响应类型与 API 函数 |
| `src/views/<业务>/list.vue` | 列表布局、查询和操作入口 |
| `src/views/<业务>/data.ts` | 表单 schema、列定义和数据转换 |
| `src/views/<业务>/modules/` | 抽屉、弹窗等局部组件 |
| `src/adapter/` | 目标 UI 库与 Vben 的组件协议适配 |
| `src/locales/langs/` | 中文、英文等语言资源 |

目录和业务文件沿用已有的小写、短横线风格，组件、类型和变量沿用同目录现有约定。优先保持 `list.vue`、`data.ts`、`modules/form.vue` 的组织方式，不把请求、表单 schema 和全部弹窗堆进一个页面。

## 复用组件

管理页面优先使用已有的 `Page`、`useVbenVxeGrid`、`useVbenForm`、`useVbenDrawer` / `useVbenModal`、`VbenTableAction`、`VbenDescriptions`。底层输入控件通过当前 AntdvNext 适配器使用。

已封装的列表、表单和弹窗不再另写一套 Antdv Table/Form/Modal 基础结构。确实需要新能力时，先确认当前封装是否支持配置，再决定是否补充适配。

完整页面示例见[新增前端页面](/development/new-page)。

## API 与类型

普通业务请求统一使用 `#/api/request` 的 `requestClient`。它负责 `/api` 前缀、Bearer、响应拆包和认证刷新；页面不重复维护这套逻辑。

下面取自角色 API 的调用方式：

```ts
import { requestClient } from '#/api/request';

export const getRole = (id: string) =>
  requestClient.get<SystemRole>(`/system/role/${id}`);
```

`SystemRole` 在同一 API 文件声明。`getRole()` 返回已经拆包的业务对象，调用方不再访问 `result.data.data`；路径也不重复写成 `/api/system/role/...`。

认证模块有专门的传输逻辑以处理刷新等例外，新业务页面不要绕过它另建登录或 token 存储方案。

- ID 声明为 `string`，不经 `Number()`、`parseInt()` 或一元 `+` 转换。
- 状态等有限值使用 `0 | 1` 等明确类型，不和字符串、布尔值混用。
- 查询、保存、列表响应分别建模，不用 `any` 或数据库实体形状代替接口契约。
- 构造保存参数时只选择可写字段，不用 `{ ...row }` 把列表记录全量提交。

角色保存参数的构造示意：

```ts
const payload: RoleSave = {
  name: values.name,
  status: values.status,
  remark: values.remark,
};
```

可选字段是否省略、清空使用 `null` 还是空串，应以具体接口约定为准，不做全局猜测。

## 表单适配

已注册的控件直接写入 `useVbenForm` schema 的 `component` 和 `componentProps`。不要用 `Input` 占位，再通过插槽手写一个已经支持的同类控件。

当前模型绑定来自 `src/adapter/form.ts`：

| 控件 | 模型属性 |
| --- | --- |
| 默认控件 | `value` |
| Checkbox、Radio、Switch | `checked` |
| RichEditor、Upload 包装组件 | `modelValue` |

新增控件要同时核对组件注册、类型映射及模型协议。上传使用既有 Upload 封装，通过 `componentProps` / `customRequest` 对接上传逻辑；不能直接假设包装组件沿用底层原生组件的所有事件。

必填由业务契约决定，不能为了显示星号而给可选字段增加必填规则。例如新增用户的角色可以不选，应按当前用户 API 处理空角色集合。

## 抽屉与异步操作

1. 每次打开重置上次表单值、目标 ID 和错误状态。
2. 编辑时按需要读取详情，避免将列表中的不完整数据直接回填。
3. 加载失败时保留明确提示并禁止提交未完成的数据。
4. 提交时锁定交互，在 `finally` 中解除锁定，防止重复请求或永久 loading。
5. 成功后关闭弹窗并通知列表刷新；失败时保留输入供修正。

复用 `CellOperation` / `VbenTableAction` 已有的删除确认，避免点击一次弹出两个确认框。错误提示与请求层分工，避免同一失败出现多条重复 toast。

## 国际化与界面文案

页面标题、字段、操作、校验和错误文案使用 `#/locales` 的 `$t`，同步维护中文和英文资源。新文件遵循当前 `src/locales/langs/zh-CN/`、`en-US/` 目录组织。

需要随语言切换更新的列和 schema，在当前响应式流程或工厂函数中重新生成，避免模块加载时就把翻译结果永久缓存。

```ts
label: $t('system.role.roleName')
```

界面面向使用者描述操作和可修正的问题。DTO、权限码、缓存机制等实现说明放进开发文档，不堆在普通表单提示中。

## 权限与公开配置

菜单、路由、按钮使用后端返回的有效权限，接入方式见[菜单与权限](/development/permissions)。按钮隐藏不代替服务端校验；新增路由组件需满足后端组件允许列表及前端实际文件映射。

`VITE_` 变量和前端产物都可被浏览器读取。只能放公开配置，不放数据库/Redis 密码、JWT 签名密钥或 OSS AccessKey，也不保存登录密码。

## 格式与检查

当前工程使用 Oxfmt 格式化，结合 ESLint、Oxlint 与 Stylelint 检查，不按 Prettier 配置维护。编辑器推荐 Vue、ESLint 和 Oxc 扩展；打开仓库根与打开 frontend 时应使用各自已提供的 `.vscode` 配置，保证工作目录正确。

遵守 `frontend/.editorconfig`：UTF-8、LF、2 空格等。格式和导入排序交给配置处理；保存格式化与 ESLint 修复是两项操作，必要时手动保存后检查差异。

业务变更后，在 **`frontend/`** 执行适用的检查：

```sh
pnpm exec eslint apps/web-antdv-next/src --quiet
pnpm exec oxfmt --check apps/web-antdv-next/src
pnpm --filter @vben/web-antdv-next typecheck
pnpm exec vitest run apps/web-antdv-next/src --dom
pnpm --filter @vben/web-antdv-next build
```

修改共享 packages 时还要检查受影响的引用。自动修复后重新查看 diff，不能把“格式化成功”当成业务功能已经验证。
