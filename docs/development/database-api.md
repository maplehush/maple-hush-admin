# 数据库与接口

本页给出公告示例的数据库与后端主链路。以下为教学参考实现，不是仓库内置的公告模块。请在自己的开发分支创建文件，并在实际接入后完成代码检查和联调。

## 1. 新增 Flyway 迁移

目标目录：`backend/admin-system-start/src/main/resources/db/migration/`。

先查看现有最大版本，再选择下一个未占用版本，例如 `V<N>__create_biz_notice.sql`；将 `<N>` 替换为实际数字。多人开发需要协调版本，不能直接照抄某个固定的下一版本。

```sql
CREATE TABLE biz_notice (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '公告标识',
    title VARCHAR(200) NOT NULL COMMENT '公告标题',
    content TEXT NOT NULL COMMENT '纯文本内容，应用限制 5000 字符',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0 停用，1 启用',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '修改时间',
    PRIMARY KEY (id),
    KEY idx_notice_status_time (status, create_time, id),
    CONSTRAINT chk_notice_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

不手工执行迁移后又让 Flyway 重复执行；不修改已进入历史表的迁移。此脚本只建公告表，菜单与角色通过[菜单与权限](./permissions.md)接入，不写死菜单 ID、用户 ID 或密码。

## 2. 实体与 Mapper

以下文件放在 `admin-notice-biz/src/main/java/com/maplehush/admin/notice/` 对应子包。

::: code-group

```java [entity/Notice.java]
package com.maplehush.admin.notice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.maplehush.admin.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 公告实体；HTTP 接口使用 VO，不直接返回实体。
 */
@Getter
@Setter
@TableName("biz_notice")
public class Notice extends BaseEntity {
    /**
     * 数据库自增标识。
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 公告标题。
     */
    private String title;
    /**
     * 纯文本正文。
     */
    private String content;
    /**
     * 0 停用，1 启用。
     */
    private Integer status;
}
```

```java [mapper/NoticeMapper.java]
package com.maplehush.admin.notice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.maplehush.admin.notice.entity.Notice;
import org.apache.ibatis.annotations.Mapper;

/**
 * 公告数据访问。
 */
@Mapper
public interface NoticeMapper extends BaseMapper<Notice> {}
```

:::

`BaseEntity` 只提供 createTime、updateTime，主键由实体自己声明。已有时间填充器负责插入和实体更新时的时间；Wrapper-only 更新需要显式设置 updateTime，不能假设自动填充会触发。

## 3. 写入 DTO、查询 DTO 与 VO

::: code-group

```java [dto/NoticeSaveDTO.java]
package com.maplehush.admin.notice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 公告新增与全量修改表单。
 *
 * @param title 标题
 * @param content 纯文本内容
 * @param status 0 停用，1 启用
 */
public record NoticeSaveDTO(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 5000) String content,
        @NotNull @Min(0) @Max(1) Integer status) {}
```

```java [dto/NoticeQueryDTO.java]
package com.maplehush.admin.notice.dto;

import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;

/**
 * 已校验的分页查询。
 *
 * @param page 页码，从 1 开始
 * @param pageSize 每页 1 至 100 条
 * @param title 可选标题关键字
 * @param status 可选状态
 */
public record NoticeQueryDTO(int page, int pageSize, String title, Integer status) {
    /**
     * 将 HTTP 字符串参数转换为业务查询。
     *
     * @param page 页码
     * @param pageSize 页大小
     * @param title 标题关键字
     * @param status 状态
     * @return 校验后的查询
     */
    public static NoticeQueryDTO parse(
            String page, String pageSize, String title, String status) {
        if (title != null && title.length() > 200) {
            throw invalid("标题查询过长");
        }
        Integer state = null;
        if (status != null) {
            state = switch (status) {
                case "0" -> 0;
                case "1" -> 1;
                default -> throw invalid("状态须为 0 或 1");
            };
        }
        return new NoticeQueryDTO(
                positive(page, Integer.MAX_VALUE), positive(pageSize, 100),
                title == null || title.isBlank() ? null : title.strip(), state);
    }

    private static int positive(String value, int max) {
        try {
            if (value == null || !value.matches("[1-9][0-9]{0,9}")) {
                throw new NumberFormatException();
            }
            int result = Integer.parseInt(value);
            if (result > max) {
                throw new NumberFormatException();
            }
            return result;
        } catch (NumberFormatException exception) {
            throw invalid("分页参数无效，pageSize 须为 1 至 100");
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
    }
}
```

```java [vo/NoticeVO.java]
package com.maplehush.admin.notice.vo;

/**
 * HTTP 公告出参。
 *
 * @param id 十进制字符串标识
 * @param title 标题
 * @param content 纯文本内容
 * @param status 状态
 * @param createTime 东八区创建时间
 * @param updateTime 东八区更新时间
 */
public record NoticeVO(
        String id, String title, String content, Integer status,
        String createTime, String updateTime) {}
```

:::

这里通过 VO 的 String id 明确保证 HTTP ID 不经过 JavaScript number。内部 NoticeSummary 的 Long id 则是 Java 模块间契约，不直接拿它作为 HTTP 出参。

## 4. Service 接口

文件：`service/INoticeService.java`。

```java
package com.maplehush.admin.notice.service;

import com.maplehush.admin.common.api.PageResult;
import com.maplehush.admin.notice.dto.NoticeQueryDTO;
import com.maplehush.admin.notice.dto.NoticeSaveDTO;
import com.maplehush.admin.notice.vo.NoticeVO;

/**
 * 公告管理契约；调用方注入接口。
 */
public interface INoticeService {
    /**
     * @param query 已校验的查询条件
     * @return 当前页及总条数
     */
    PageResult<NoticeVO> list(NoticeQueryDTO query);
    /**
     * @param id 目标标识
     * @return 公告详情，不存在时抛出业务异常
     */
    NoticeVO get(Long id);
    /**
     * @param dto 已校验的完整表单
     * @return 新标识
     */
    Long create(NoticeSaveDTO dto);
    /**
     * @param id 目标标识
     * @param dto 已校验的完整表单
     */
    void update(Long id, NoticeSaveDTO dto);
    /**
     * @param id 目标标识
     */
    void delete(Long id);
}
```

## 5. Service 实现、事务与审计

文件：`service/impl/NoticeServiceImpl.java`。下面复用已有公共技术能力；不会给系统新增事务管理器、数据源或审计表。

```java
package com.maplehush.admin.notice.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.maplehush.admin.common.annotation.OperationLog;
import com.maplehush.admin.common.api.PageResult;
import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.notice.api.NoticeQuery;
import com.maplehush.admin.notice.api.NoticeSummary;
import com.maplehush.admin.notice.dto.NoticeQueryDTO;
import com.maplehush.admin.notice.dto.NoticeSaveDTO;
import com.maplehush.admin.notice.entity.Notice;
import com.maplehush.admin.notice.mapper.NoticeMapper;
import com.maplehush.admin.notice.service.INoticeService;
import com.maplehush.admin.notice.vo.NoticeVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * 公告规则与事务边界，同时实现供其他模块使用的查询契约。
 */
@Service
public class NoticeServiceImpl implements INoticeService, NoticeQuery {
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final NoticeMapper mapper;

    public NoticeServiceImpl(NoticeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<NoticeVO> list(NoticeQueryDTO query) {
        var wrapper = Wrappers.<Notice>lambdaQuery()
                .like(query.title() != null, Notice::getTitle, query.title())
                .eq(query.status() != null, Notice::getStatus, query.status())
                .orderByDesc(Notice::getCreateTime, Notice::getId);
        var page = mapper.selectPage(new Page<Notice>(query.page(), query.pageSize()), wrapper);
        return new PageResult<>(page.getRecords().stream().map(this::vo).toList(), page.getTotal());
    }

    @Override
    @Transactional(readOnly = true)
    public NoticeVO get(Long id) {
        Notice notice = mapper.selectById(id);
        if (notice == null) {
            throw missing();
        }
        return vo(notice);
    }

    @Override
    @Transactional
    @OperationLog(value = "system:notice:create", targetType = "notice")
    public Long create(NoticeSaveDTO dto) {
        Notice notice = new Notice();
        notice.setTitle(dto.title().strip());
        notice.setContent(dto.content().strip());
        notice.setStatus(dto.status());
        if (mapper.insert(notice) != 1) {
            throw new IllegalStateException("Notice insert did not affect one row");
        }
        return notice.getId();
    }

    @Override
    @Transactional
    @OperationLog(value = "system:notice:update", targetType = "notice", targetIdArgument = 0)
    public void update(Long id, NoticeSaveDTO dto) {
        int changed = mapper.update(null, Wrappers.<Notice>lambdaUpdate()
                .eq(Notice::getId, id)
                .set(Notice::getTitle, dto.title().strip())
                .set(Notice::getContent, dto.content().strip())
                .set(Notice::getStatus, dto.status())
                .set(Notice::getUpdateTime,
                        LocalDateTime.now(ZoneId.of("Asia/Shanghai")).withNano(0)));
        if (changed != 1) {
            throw missing();
        }
    }

    @Override
    @Transactional
    @OperationLog(value = "system:notice:delete", targetType = "notice", targetIdArgument = 0)
    public void delete(Long id) {
        if (mapper.deleteById(id) != 1) {
            throw missing();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NoticeSummary> findEnabled(Long id) {
        if (id == null || id <= 0) {
            return Optional.empty();
        }
        Notice notice = mapper.selectOne(Wrappers.<Notice>lambdaQuery()
                .eq(Notice::getId, id).eq(Notice::getStatus, 1));
        return Optional.ofNullable(notice)
                .map(value -> new NoticeSummary(value.getId(), value.getTitle()));
    }

    private NoticeVO vo(Notice notice) {
        return new NoticeVO(notice.getId().toString(), notice.getTitle(), notice.getContent(),
                notice.getStatus(), notice.getCreateTime().format(TIME),
                notice.getUpdateTime().format(TIME));
    }

    private BusinessException missing() {
        return new BusinessException(ErrorCode.NOT_FOUND, "公告不存在或已删除");
    }
}
```

审计注解放在实现类公共写方法；现有 system-biz 的审计切面在启动应用中提供统一处理。更新、删除的参数 0 是 Long id，才能被 targetIdArgument 识别；新增时 ID 尚未生成，不能把整个 DTO 当审计目标或将正文写入摘要。

该示例没有部门数据范围、作者所有权或乐观锁功能。若业务需要这些规则，应在 Service 和 SQL 条件中明确实现，不能仅凭菜单权限推断已有数据范围控制。

## 6. Controller 与接口权限

文件：`controller/NoticeController.java`。

```java
package com.maplehush.admin.notice.controller;

import com.maplehush.admin.common.api.PageResult;
import com.maplehush.admin.common.api.Result;
import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.notice.dto.NoticeQueryDTO;
import com.maplehush.admin.notice.dto.NoticeSaveDTO;
import com.maplehush.admin.notice.service.INoticeService;
import com.maplehush.admin.notice.vo.NoticeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 公告管理 HTTP 接口，权限与页面是否显示无关。
 */
@Tag(name = "公告管理")
@RestController
@RequestMapping("/api/notice")
public class NoticeController {
    private final INoticeService service;

    public NoticeController(INoticeService service) {
        this.service = service;
    }

    @Operation(summary = "分页查询公告")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:notice:list')")
    public Result<PageResult<NoticeVO>> list(
            @RequestParam(defaultValue = "1") String page,
            @RequestParam(defaultValue = "20") String pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String status) {
        return Result.ok(service.list(NoticeQueryDTO.parse(page, pageSize, title, status)));
    }

    @Operation(summary = "查询公告详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:notice:list')")
    public Result<NoticeVO> get(@PathVariable String id) {
        return Result.ok(service.get(id(id)));
    }

    @Operation(summary = "新增公告")
    @PostMapping
    @PreAuthorize("hasAuthority('system:notice:create')")
    public Result<Map<String, String>> create(@Valid @RequestBody NoticeSaveDTO dto) {
        return Result.ok(Map.of("id", service.create(dto).toString()));
    }

    @Operation(summary = "修改公告")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:notice:update')")
    public Result<Void> update(@PathVariable String id, @Valid @RequestBody NoticeSaveDTO dto) {
        service.update(id(id), dto);
        return Result.ok(null);
    }

    @Operation(summary = "删除公告")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:notice:delete')")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id(id));
        return Result.ok(null);
    }

    private Long id(String value) {
        try {
            if (!value.matches("[1-9][0-9]{0,18}")) {
                throw new NumberFormatException();
            }
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "公告 ID 无效");
        }
    }
}
```

以上入口沿用现有安全链与方法权限，不新增匿名放行规则，不关闭 CSRF 或 Origin 检查。DTO 校验失败由现有全局异常处理转换，业务不存在使用既有 ErrorCode，不额外发明一套 code。

下一步编写[前端页面](./new-page.md)，随后再添加菜单和角色授权。
