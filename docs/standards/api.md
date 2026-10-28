# 接口规范

前后端以相同的请求路径、字段类型、响应和权限码协作。新增功能的联调过程见[接口联调](/development/api-integration)。

## 路径、方法与输入

浏览器统一通过同源 `/api` 访问后端。Controller 声明完整接口路径，前端 `requestClient` 已配置 `/api` 前缀，业务 API 文件只填写其后的路径。

现有角色接口可以作为命名参考：

| 方法 | 完整路径 | 用途 |
| --- | --- | --- |
| GET | `/api/system/role/list` | 分页查询 |
| GET | `/api/system/role/{id}` | 详情 |
| POST | `/api/system/role` | 新增 |
| PUT | `/api/system/role/{id}` | 修改 |
| DELETE | `/api/system/role/{id}` | 删除 |

GET 使用查询参数，写操作按接口定义传 JSON 或 multipart。DTO 只接受允许修改的字段，后端完成类型、长度、枚举、范围和业务校验。不要为方便前端而把服务端维护的 ID、内置标志、权限版本等全部开放写入。

## 统一响应

普通业务接口使用 `Result<T>`：

```json
{
  "code": 0,
  "data": { "id": "123" },
  "error": null,
  "message": "ok"
}
```

| 字段 | 含义 |
| --- | --- |
| `code` | 稳定业务码，`0` 表示成功，非 `0` 表示失败 |
| `data` | 业务结果；无结果或失败时可以为 `null` |
| `error` | 稳定错误标识；成功时为 `null` |
| `message` | 可供展示的安全提示，不作为稳定判断条件 |

**HTTP 状态与业务 code 是两层信息**。例如 HTTP 401 对应业务 `40100`，二者不是同一个数字。不要用 `200` 代替成功业务码，也不把所有业务异常包装成 HTTP 200。

### 认证刷新是现有例外

`POST /api/auth/refresh` 成功体为 JSON 字符串形式的 access token，不是 `Result<T>`。认证传输层对此单独处理；不要在业务请求层再次包装刷新请求，也不要按 `data.accessToken` 解析该响应。

首次登录若返回需要改密的分支，要完成指定改密流程后才能进入正常会话，不能仅看到 `code = 0` 就假设已经取得普通 access token。

## 分页、ID 与时间

当前列表查询通常使用 `page`、`pageSize`，页码从 1 开始；最大页大小以接口校验为准。分页结果在 `data` 中使用 `items` 和 `total`：

```json
{
  "code": 0,
  "data": { "items": [], "total": 0 },
  "error": null,
  "message": "ok"
}
```

| 数据 | 数据库 / Java | JSON / TypeScript |
| --- | --- | --- |
| 资源 ID、关联 ID | 有符号 `BIGINT` / `Long` | 十进制字符串 / `string` |
| 页码、页大小、总数 | 整数 | 数字 |
| 启用状态 | 按表定义的整数 | 如 `0` 或 `1`，不是 `"0"` / `"1"` |
| 日期时间 | 项目约定的时间类型 | `yyyy-MM-dd HH:mm:ss` 字符串 |

JavaScript number 无法精确表示所有 Long 值。ID 在传递、比较、路由与表单中保持字符串；不能先转 number 再转回来。具体父节点字段是否接受 `0` 或 `null`，以该接口为准，不将根节点约定推广到全部 ID。

时间统一按 `Asia/Shanghai` 约定处理，不在某个页面私自加减 8 小时。日期范围的开始、结束边界按具体查询契约实现，不能假设所有接口都使用相同的开闭区间。

## 公共错误码

下表对应后端 `ResultCodes` 和 `ErrorCode`。新增公共码时需要同时维护后端定义、客户端识别与本表；具体接口不重复罗列整套通用码表，只补充自己的触发条件。

| code | error | HTTP | 含义 |
| --- | --- | --- | --- |
| `0` | `null` | 200 | 业务处理成功 |
| `40000` | `BAD_REQUEST` | 400 | 请求格式或参数类型错误 |
| `40010` | `INVALID_ARGUMENT` | 400 | 参数不符合校验规则 |
| `40011` | `CAPTCHA_INVALID` | 400 | 验证码错误、过期或已消费 |
| `40100` | `UNAUTHENTICATED` | 401 | 未登录、凭据无效或会话失效 |
| `40300` | `FORBIDDEN` | 403 | 没有操作权限 |
| `40301` | `CSRF_INVALID` | 403 | CSRF 凭据或请求来源校验失败 |
| `40400` | `NOT_FOUND` | 404 | 资源不存在或已删除 |
| `40900` | `CONFLICT` | 409 | 唯一约束、关联或并发状态冲突 |
| `42900` | `RATE_LIMITED` | 429 | 请求频率超限 |
| `50000` | `INTERNAL_ERROR` | 500 | 未预期的内部异常 |
| `50300` | `DEPENDENCY_UNAVAILABLE` | 503 | 必要依赖暂不可用 |

错误形状示例：

```json
{
  "code": 40300,
  "data": null,
  "error": "FORBIDDEN",
  "message": "无权执行该操作"
}
```

使用 `code` 或 `error` 作稳定识别，`message` 供展示。前端已有错误映射及认证处理，新页面不要根据某句中文提示决定是否刷新、退出或重试。

## 认证与授权

- 普通受保护接口携带 `Authorization: Bearer <accessToken>`，使用现有请求层管理。
- refresh 使用 HttpOnly Cookie；浏览器业务代码不自行读取该 Cookie。
- 刷新、退出沿用 CSRF 流程：先获取 `/api/auth/csrf` 的 `data.token`，按现有实现带 `X-XSRF-TOKEN`。登录、刷新、退出还受来源校验约束。
- 首次强制改密使用专用改密令牌，不能把它当作普通 access token 访问业务接口。
- 接口使用 Spring Security 校验权限，Service 同时检查目标范围和内置数据保护。

例如 `system:user:list` 控制用户列表读取。菜单显示和按钮显隐帮助用户发现可用操作，不能代替 API 权限校验。新增权限码和组件映射按[菜单与权限](/development/permissions)维护。

## 接口文档与变更

Controller 的用途、请求字段及必要约束要能在 OpenAPI 中看懂；DTO/VO 注释不应只有类型名。公共响应和错误说明统一维护，避免每个接口堆叠相同错误表格。

Knife4j / OpenAPI 默认仅在开发、测试环境开放，生产关闭。调试不能通过放宽权限或来源检查解决。

修改路径、必填项、类型、错误语义或认证流程前，核对现有前端调用和测试；同步更新 API 类型、相关页面与使用文档，明确兼容影响。
