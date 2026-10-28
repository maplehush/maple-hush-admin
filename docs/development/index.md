# 开发指南

本章以“公告管理”为教学例子，说明拿到项目后如何增加业务模块、数据库表、接口和管理页面，并接入现有菜单与权限。

::: info 示例边界
公告管理不是仓库已有功能。下文文件路径、表和接口是你二次开发时要新增的内容，请在自己的开发分支接入。
:::

建议先完成[快速开始](/getting-started/)，再在自己的开发分支和开发库中按下列顺序操作。

## 开发顺序

| 顺序 | 页面 | 你要完成什么 |
| --- | --- | --- |
| 1 | [新增业务模块](./new-module.md) | 注册 Maven 模块，接入启动模块与内部契约 |
| 2 | [数据库与接口](./database-api.md) | 编写 Flyway、实体、DTO/VO、Mapper、Service 和 Controller |
| 3 | [新增前端页面](./new-page.md) | 请求层、国际化、列表和抽屉表单 |
| 4 | [菜单与权限](./permissions.md) | 登记页面组件，配置菜单、按钮和角色 |
| 5 | [接口联调](./api-integration.md) | 对照预期结果检查请求链路与授权 |

## 全章统一约定

| 项目 | 本章示例 |
| --- | --- |
| Maven 模块 | admin-notice-api、admin-notice-biz |
| Java 包 | com.maplehush.admin.notice |
| 数据表 | biz_notice |
| 接口前缀 | /api/notice |
| 页面访问路径 | /content/notice |
| 页面组件键 | /notice/list |
| 前端页面 | frontend/apps/web-antdv-next/src/views/notice/list.vue |
| 权限码 | system:notice:list / create / update / delete（每项均使用完整前缀） |

当前系统菜单校验限定 `system:resource:action`，因此示例继续使用 `system:notice:*` 的结构。这里的 `*` 仅用于概括四项操作，不是让你在菜单中填写通配符。换成其他权限命名空间，需要另行调整后端校验规则及相关约定。

## 示例功能与字段

功能限于新增、编辑、删除、详情与分页查询，不增加富文本、附件、审批或发布计划。

| 字段 | 前端 / HTTP 类型 | 规则 |
| --- | --- | --- |
| id | string | 数据库 bigint 自增，HTTP 使用正整数十进制字符串 |
| title | string | 必填，去除首尾空白，最多 200 字符 |
| content | string | 必填，纯文本，最多 5000 字符 |
| status | 0 或 1 | 0 停用，1 启用；管理列表可查看两种状态 |
| createTime / updateTime | string | 后端生成，格式 yyyy-MM-dd HH:mm:ss |

公告内容使用纯文本输入，展示时用 Vue 文本插值，不使用 `v-html`。`status=1` 在本例表示启用，不等于向匿名用户公开。

## 接口契约

| 方法 | 路径 | 参数 / 返回 data | 所需权限 |
| --- | --- | --- | --- |
| GET | /api/notice/list | page、pageSize、title、status；返回 items / total | system:notice:list |
| GET | /api/notice/{id} | 返回一条 NoticeVO | system:notice:list |
| POST | /api/notice | title、content、status；返回字符串 id | system:notice:create |
| PUT | /api/notice/{id} | 全量提交 title、content、status；返回 null | system:notice:update |
| DELETE | /api/notice/{id} | 返回 null | system:notice:delete |

分页从 1 开始，pageSize 为 1～100，默认 20；标题模糊查询，状态精确匹配；排序固定为创建时间倒序、ID 倒序。详情不存在以及修改/删除未命中返回 NOT_FOUND。

普通响应沿用 `Result<T>`，成功时 `code=0`。前端 requestClient 已拆包，页面接收 data，不再自行读取一次 response.data。

## 现有代码怎么参考

- 后端分层、分页和事务：查看 system/role 与 system/dict。
- 前端 Page、Grid 和 Drawer：查看 views/system/role 与 views/system/dept。
- 组件登记和菜单校验：查看 system/menu/support/SysMenuInput 及 menu-components.txt。

代码位置以[目录结构](/introduction/directory)为入口。开发规范栏目将进一步归纳规则；本章先结合当前工程的实际实现说明如何接入。
