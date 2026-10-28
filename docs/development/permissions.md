# 菜单与权限

有了页面和接口，还需要把它们接到现有菜单与角色体系。本例将“公告管理”放到“内容管理”目录下。

## 三种配置要对应

| 配置 | 本例的值 | 决定什么 |
| --- | --- | --- |
| 路由访问路径 | /content/notice | 浏览器进入哪个地址 |
| 页面组件键 | /notice/list | 加载 views 下的哪个 Vue 文件 |
| 权限码 | system:notice:list 等 | 当前用户是否能访问页面或执行操作 |

访问路径与组件文件路径不要求完全相同：本例 URL 位于 content 目录，组件实际放在 `views/notice/list.vue`。不要把 Java 模块名填成 component，也不要给 component 加 .vue 后缀。

## 1. 登记页面组件

修改现有文件：

```text
backend/admin-system-biz/src/main/resources/menu-components.txt
```

在保留原有内容的基础上追加一行：

```text
/notice/list
```

后端 `SysMenuInput` 会从这个资源读取允许的组件键，菜单新增/修改时校验。只创建 Vue 文件而没有登记，菜单保存会提示组件未登记。

资源在应用初始化时读取。修改后按自己的运行方式重新加载后端资源并重启；运行 Jar 时需要重新打包，不能只修改源码文件后重启旧 Jar。不要在另一个模块创建同名清单文件。

## 2. 创建内容管理目录

使用具备菜单维护权限的账号，进入系统管理 → 菜单管理，新增目录：

| 表单项 | 值 |
| --- | --- |
| 名称 name | Content |
| 标题 meta.title | 内容管理 |
| 类型 type | catalog |
| 路径 path | /content |
| 组件 component | 根目录使用 BasicLayout（按现有表单选项填写） |
| 权限码 authCode | 留空，目录不设置权限码 |
| 状态 | 启用 |

先保存目录，再创建子页面。目录尚无子页面时，不要填写指向一个不存在页面的重定向。name 和 path 必须与现有节点不冲突。

## 3. 创建公告页面

在内容管理下新增菜单：

| 表单项 | 值 |
| --- | --- |
| 上级 pid | 刚创建的内容管理目录 ID |
| 名称 name | NoticeManage |
| 标题 meta.title | 公告管理 |
| 类型 type | menu |
| 完整路径 path | /content/notice |
| 组件 component | /notice/list |
| 权限码 authCode | system:notice:list |
| 状态 | 启用 |

管理接口接收完整 path，后端按父节点转换存储；使用界面或管理 API 时不要自己提前截成 notice。`GET /api/menu/all` 返回的是当前用户可见路由，和用于管理的 `GET /api/system/menu/list` 不是同一份数据。

如直接调用菜单新增接口，其请求结构如下。pid 必须换成真实目录 ID，不把示例占位文本提交给后端：

```json
{
  "pid": "替换为内容管理目录ID",
  "name": "NoticeManage",
  "type": "menu",
  "path": "/content/notice",
  "component": "/notice/list",
  "authCode": "system:notice:list",
  "status": 1,
  "meta": { "title": "公告管理", "order": 10 }
}
```

## 4. 添加操作按钮

在公告页面下增加三个 button 节点：

| 名称 name | 标题 | authCode |
| --- | --- | --- |
| NoticeCreate | 新增公告 | system:notice:create |
| NoticeUpdate | 编辑公告 | system:notice:update |
| NoticeDelete | 删除公告 | system:notice:delete |

按钮节点只登记操作权限，不配置 path/component，也不会作为页面出现在左侧导航。查询权限由页面节点提供，不必为同一个查询码再建重复节点。

当前校验要求小写 `system:resource:action`。本例四项权限必须在菜单、前端 can() 和后端 @PreAuthorize 中保持完全一致，不能一处写 notice、另一处写 announcement。

## 5. 给普通角色授权

使用有角色维护和菜单授权能力的账号，在角色管理中为开发用普通角色选择：

- 内容管理目录。
- 公告管理页面，提供查询权限。
- 该角色实际需要的新增、编辑、删除按钮。

再将角色分配给测试用普通用户。角色菜单和用户角色变更会影响授权版本，相关用户可能需要重新登录；按当前应用提示处理，不只改本地 accessCodes 来伪造权限。

内置超级管理员具有特殊授权能力。验证普通角色效果时使用普通账号，不只用超级管理员判断“按钮隐藏是否正确”。只有新增权限但没有查询权限，不能完成本例列表和编辑详情流程，应一并授予页面查询权限。

## 6. 前后端分别控制什么

| 位置 | 控制内容 |
| --- | --- |
| 动态菜单 / 路由 | 用户可进入哪些业务页面 |
| can()、CellOperation / VbenTableAction | 页面上的操作入口是否显示 |
| Controller 的 @PreAuthorize | 请求是否有执行该接口的权限 |
| Service / Mapper | 业务规则、目标数据和事务一致性 |

前端隐藏按钮并不意味着 API 已安全，后端校验不能省。反过来，拥有某个 API 权限也不会自动创建 Vue 页面或登记菜单。

菜单由 `src/router/access.ts` 调用后端菜单并映射 `views/**/*.vue`。本例不需要再在静态路由里重复添加同一个业务路径，避免出现两套路由和权限来源。

菜单标题若要随语言切换，还需参照 `src/router/menu-locale.ts` 中的项目映射方式增加对应项；仅添加 notice.json 不会自动把数据库中的任意中文标题变成多语言。

完成后进入[接口联调](./api-integration.md)。
