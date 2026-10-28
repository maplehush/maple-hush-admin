# Database and APIs

This page provides the database and main backend flow for the announcement example. It is a teaching reference implementation, not a built-in announcement module. Create the files on your own development branch, then complete code checks and integration testing after connecting them.

## 1. Add a Flyway migration

Target directory: `backend/admin-system-start/src/main/resources/db/migration/`.

Check the highest existing version and choose the next unused version, such as `V<N>__create_biz_notice.sql`, replacing `<N>` with an actual number. Coordinate versions when several people develop in parallel rather than copying a fixed next version from an example.

```sql
CREATE TABLE biz_notice (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Announcement identifier',
    title VARCHAR(200) NOT NULL COMMENT 'Announcement title',
    content TEXT NOT NULL COMMENT 'Plain text content, limited to 5000 characters by the application',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0 disabled, 1 enabled',
    create_time DATETIME NOT NULL COMMENT 'Creation time',
    update_time DATETIME NOT NULL COMMENT 'Update time',
    PRIMARY KEY (id),
    KEY idx_notice_status_time (status, create_time, id),
    CONSTRAINT chk_notice_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

Do not execute a migration manually and then let Flyway execute it again. Do not modify migrations already recorded in the history table. This script creates only the announcement table. Connect menus and roles through [Menus and Permissions](./permissions.md), without hardcoding menu IDs, user IDs, or passwords.

## 2. Entity and Mapper

Place the following files in the corresponding subpackages under `admin-notice-biz/src/main/java/com/maplehush/admin/notice/`.

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
 * Announcement entity; HTTP endpoints return VOs rather than entities.
 */
@Getter
@Setter
@TableName("biz_notice")
public class Notice extends BaseEntity {
    /**
     * Database auto-incrementing identifier.
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * Announcement title。
     */
    private String title;
    /**
     * Plain text content.
     */
    private String content;
    /**
     * 0 disabled, 1 enabled。
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
 * Announcement data access.
 */
@Mapper
public interface NoticeMapper extends BaseMapper<Notice> {}
```

:::

`BaseEntity` provides only createTime and updateTime; each entity declares its own primary key. The existing time filler handles timestamps on insert and entity updates. Wrapper-only updates must explicitly set updateTime; do not assume automatic filling will run.

## 3. Write DTO, query DTO, and VO

::: code-group

```java [dto/NoticeSaveDTO.java]
package com.maplehush.admin.notice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Form for creating and fully updating an announcement.
 *
 * @param title title
 * @param content plain text content
 * @param status 0 disabled, 1 enabled
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
 * Validated pagination query.
 *
 * @param page page number, starting at 1
 * @param pageSize 1 to 100 records per page
 * @param title optional title keyword
 * @param status optional status
 */
public record NoticeQueryDTO(int page, int pageSize, String title, Integer status) {
    /**
     * Converts HTTP string parameters into a business query.
     *
     * @param page page number
     * @param pageSize page size
     * @param title title keyword
     * @param status status
     * @return validated query
     */
    public static NoticeQueryDTO parse(
            String page, String pageSize, String title, String status) {
        if (title != null && title.length() > 200) {
            throw invalid("Title query is too long");
        }
        Integer state = null;
        if (status != null) {
            state = switch (status) {
                case "0" -> 0;
                case "1" -> 1;
                default -> throw invalid("Status must be 0 or 1");
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
            throw invalid("Invalid pagination parameters; pageSize must be 1 to 100");
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
 * HTTP announcement response.
 *
 * @param id decimal string identifier
 * @param title title
 * @param content plain text content
 * @param status status
 * @param createTime creation time in UTC+8
 * @param updateTime update time in UTC+8
 */
public record NoticeVO(
        String id, String title, String content, Integer status,
        String createTime, String updateTime) {}
```

:::

The VO's String id explicitly ensures that HTTP IDs never pass through JavaScript number. The internal NoticeSummary Long id is a contract between Java modules and is not used directly as an HTTP response.

## 4. Service interface

File: `service/INoticeService.java`.

```java
package com.maplehush.admin.notice.service;

import com.maplehush.admin.common.api.PageResult;
import com.maplehush.admin.notice.dto.NoticeQueryDTO;
import com.maplehush.admin.notice.dto.NoticeSaveDTO;
import com.maplehush.admin.notice.vo.NoticeVO;

/**
 * Announcement management contract; callers inject the interface.
 */
public interface INoticeService {
    /**
     * @param query validated query criteria
     * @return current page and total record count
     */
    PageResult<NoticeVO> list(NoticeQueryDTO query);
    /**
     * @param id target identifier
     * @return announcement details; throws a business exception if missing
     */
    NoticeVO get(Long id);
    /**
     * @param dto validated complete form
     * @return new identifier
     */
    Long create(NoticeSaveDTO dto);
    /**
     * @param id target identifier
     * @param dto validated complete form
     */
    void update(Long id, NoticeSaveDTO dto);
    /**
     * @param id target identifier
     */
    void delete(Long id);
}
```

## 5. Service implementation, transactions, and auditing

File: `service/impl/NoticeServiceImpl.java`. The following implementation reuses existing common infrastructure without adding a transaction manager, data source, or audit table.

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
 * Announcement rules and transaction boundaries; also implements the query contract for other modules.
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
        return new BusinessException(ErrorCode.NOT_FOUND, "Announcement does not exist or has been deleted");
    }
}
```

Place audit annotations on public write methods in the implementation class. The existing system-biz audit aspect provides shared handling in the running application. Argument 0 for update and delete is Long id, allowing targetIdArgument to recognize it. During creation the ID does not yet exist; do not use the whole DTO as the audit target or put the content into the summary.

The example has no department data scope, author ownership, or optimistic locking. If your business requires these rules, implement them explicitly in the Service and SQL conditions. Menu permissions alone do not imply data scope control.

## 6. Controller and API permissions

File: `controller/NoticeController.java`.

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
 * Announcement management HTTP endpoints; permissions are independent of page visibility.
 */
@Tag(name = "Announcement Management")
@RestController
@RequestMapping("/api/notice")
public class NoticeController {
    private final INoticeService service;

    public NoticeController(INoticeService service) {
        this.service = service;
    }

    @Operation(summary = "List announcements with pagination")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:notice:list')")
    public Result<PageResult<NoticeVO>> list(
            @RequestParam(defaultValue = "1") String page,
            @RequestParam(defaultValue = "20") String pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String status) {
        return Result.ok(service.list(NoticeQueryDTO.parse(page, pageSize, title, status)));
    }

    @Operation(summary = "Get announcement details")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:notice:list')")
    public Result<NoticeVO> get(@PathVariable String id) {
        return Result.ok(service.get(id(id)));
    }

    @Operation(summary = "Create announcement")
    @PostMapping
    @PreAuthorize("hasAuthority('system:notice:create')")
    public Result<Map<String, String>> create(@Valid @RequestBody NoticeSaveDTO dto) {
        return Result.ok(Map.of("id", service.create(dto).toString()));
    }

    @Operation(summary = "Update announcement")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:notice:update')")
    public Result<Void> update(@PathVariable String id, @Valid @RequestBody NoticeSaveDTO dto) {
        service.update(id(id), dto);
        return Result.ok(null);
    }

    @Operation(summary = "Delete announcement")
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
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Invalid announcement ID");
        }
    }
}
```

These endpoints use the existing security chain and method permissions, with no new anonymous access rules and with CSRF and Origin checks retained. The existing global exception handler converts DTO validation failures. Missing business records use the existing ErrorCode rather than introducing another code scheme.

Next, implement the [Frontend Page](./new-page.md), then add menus and role grants.
