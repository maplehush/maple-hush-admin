# 后端规范

后端采用模块化单体，多个 Maven 模块最终组成一个 Spring Boot 应用。新增模块的完整接入过程见[新增业务模块](/development/new-module)。

## 模块与依赖

| 模块 | 职责 | 约束 |
| --- | --- | --- |
| `admin-base-core` | 响应、异常、公共配置等底座 | 不依赖具体业务模块 |
| `admin-system-api` | 系统模块对其他模块暴露的能力与传输类型 | 不放 Controller、Mapper 或持久化实体 |
| `admin-system-biz` | 系统业务实现 | 通过接口向其他模块提供能力 |
| `admin-system-start` | 启动、模块装配与运行资源 | 汇总运行依赖和数据库迁移 |

新业务沿用 `admin-<业务>-api` / `admin-<业务>-biz`。跨模块调用依赖对方的 API 接口，避免直接调用其他业务模块的 Mapper，也不通过 HTTP 调用同一进程内的业务。

API 接口只声明实际需要的业务能力，不继承 MyBatis-Plus `IService` 暴露整套通用 CRUD。依赖版本优先由父工程管理；新增模块使用 Lombok 时仍要显式声明依赖，`provided` 依赖不会自动作为传递依赖供它编译。

## 包与命名

包名前缀为 `com.maplehush.admin`，按业务功能分包，再划分 `controller`、`service`、`service.impl`、`mapper`、`entity`、`dto`、`vo` 等目录。

| 类型 | 现有命名示例 | 规则 |
| --- | --- | --- |
| 实体 | `SysUser`、`SysDept` | 系统对象统一 `Sys` 前缀，不追加 `Entity` / `DO` |
| 输入对象 | `SysRoleQueryDTO`、`SysRoleSaveDTO` | 输入和查询条件使用独立 DTO |
| HTTP 输出 | `SysRoleVO` | 不直接返回数据库实体 |
| Service 接口 | `ISysRoleService` | 放在 `service/`，调用方注入接口 |
| Service 实现 | `SysRoleServiceImpl` | 放在 `service/impl/` |
| Mapper | `SysRoleMapper` | 负责数据库访问 |

现有业务方法使用 `listRoles`、`getRole`、`saveRole`、`updateRole`、`removeRole` 等有业务含义的名称。沿用所在模块的命名，不用含糊的 `handle`、`processData` 代替业务动作。

## 三层职责

| 层 | 应当处理 | 避免处理 |
| --- | --- | --- |
| Controller | 请求绑定、`@Valid`、接口权限、响应包装 | 直接写 SQL、跨多表组织事务 |
| Service | 业务规则、目标范围、并发处理、事务和审计 | 依赖前端已校验或已隐藏按钮 |
| Mapper | 查询与更新 SQL | 决定当前用户是否有权执行业务操作 |

输入长度、非空、数值范围使用 Bean Validation；跨字段关系、内置数据保护、授权范围和状态流转在 Service 中校验。数据库唯一约束、外键及检查约束提供最终保障。

## 事务与更新

将 `@Transactional` 放在实现类的公共业务方法上，使一次业务操作涉及的写入保持一致。只读查询按需要使用 `readOnly = true`。同一个对象内部直接调用自己的方法不会经过事务代理，不能靠给私有方法加注解建立新事务。

更新和删除需要明确目标条件，并检查受影响行数。以下节选自现有角色更新流程，`candidate` 是经过校验和字段赋值的角色实体；省略了授权、锁定和其他业务处理：

```java
if (mapper.updateRole(candidate) != 1) {
    throw notFound();
}
```

该 Mapper SQL 使用 `WHERE id = #{id}` 限定目标，并显式设置 `update_time = CURRENT_TIMESTAMP`。使用实体填充时可复用现有公共字段处理；**仅使用 Wrapper 或自定义 SQL 更新时，需要显式维护 `update_time`**，不要假设实体填充必定执行。应用生成的时间按项目统一时区处理并保持秒精度。

需要原子完成的多表写入放在同一个业务事务中。唯一性预检查用于给出友好提示，仍必须保留数据库约束，防止并发请求同时通过检查。

## 查询与 SQL

- 单表查询、分页和常规更新优先使用 Lambda Wrapper，字段保持类型引用。
- 关联查询、行锁或复杂聚合按需要使用自定义 SQL，参数采用绑定变量。
- 排序字段来自服务端允许列表，不把用户传入的列名或 SQL 片段直接拼入语句。
- 列表必须限制结果量；分页、排序与索引一起考虑。
- 有 `sort` 的菜单、部门和字典按 `sort` 升序，再按 ID 升序保证顺序稳定。

不要将前端提供的整个对象直接作为实体更新。DTO 明确允许修改哪些字段，Service 显式赋值，防止修改内置标志、授权版本等受保护字段。

## 公共字段与类型

`BaseEntity` 提供创建、更新时间；具体实体自行声明主键。授权关联表和部分日志表按实际字段建模，不为了继承而增加无用字段。

数据库 ID 使用 `Long`，对外 JSON ID 使用十进制字符串，原因与示例见[接口规范](./api.md)。Java 时间字段使用业务时区约定，数据库字段以 `_time` 命名。

Java 布尔属性不加 `is` 前缀。例如角色的 `builtIn` 对应 `is_builtin`；字典的 `defaultItem` 对应 `is_default`，通过映射注解说明列名，避免序列化和属性推断歧义。

## 异常与日志

预期业务失败使用 `BusinessException` 和公共 `ErrorCode`，由统一异常处理映射 HTTP 状态及响应。不要捕获所有异常后返回成功，也不要把 SQL、堆栈或底层异常原文直接作为客户端提示。

日志使用 SLF4J 占位符，必要时记录资源 ID、固定动作和 traceId；同一异常堆栈避免在多层重复输出。

```java
log.info("Role updated: roleId={}", id);
```

不要输出密码、验证码答案、密码修改令牌、access/refresh token、完整请求体和原始个人敏感信息。实体避免使用会自动生成全字段 `toString` 的方式泄露敏感值。

需要操作审计的业务，沿用公共实现方法上的注解，例如现有角色修改：

```java
@Transactional
@OperationLog(value = "system:role:update", targetType = "role", targetIdArgument = 0)
```

`targetIdArgument = 0` 指向第一个参数中的目标 ID。审计使用固定动作和安全摘要，不把输入对象整体序列化保存。

## 注释与格式

- 类、接口、枚举、业务方法、字段和公共常量使用多行 JavaDoc，解释业务含义和约束。
- 接口方法需要时补充 `@param`、`@return`、`@throws`，不重复方法名凑注释。
- 所有条件与循环语句体写大括号，非空语句块按行展开。
- 不使用通配符导入；Java 缩进 4 空格，UTF-8、LF，文件末尾换行，遵守 `backend/.editorconfig`。

```java
/**
 * 查询角色详情；不存在时拒绝请求。
 *
 * @param id 角色标识
 * @return 角色详情
 */
SysRoleVO getRole(Long id);
```

复用 Lombok 的 `@Getter`、`@Setter`、`@RequiredArgsConstructor` 等明确能力。Hutool 仅使用已纳入工程的 `core`、`captcha`，不引入 `hutool-all` 或另建一套 JSON、数据库、JWT 工具链。

## 检查方式

业务变更完成后，在 `backend/` 使用 Java 21 和 Maven Wrapper：

::: code-group

```powershell [Windows]
.\mvnw.cmd -B -ntp verify
```

```sh [Linux / macOS]
sh mvnw -B -ntp verify
```

:::

按改动补充正常、边界、拒绝和必要的并发测试。涉及数据库的集成测试需满足对应测试的运行条件，不能将未启用或跳过写成通过；普通构建通过也不能代替迁移和真实接口联调。
