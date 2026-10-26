package com.maplehush.admin.system.log.controller;

import com.maplehush.admin.common.api.PageResult;
import com.maplehush.admin.common.api.Result;
import com.maplehush.admin.system.log.dto.SysLogQueryDTO;
import com.maplehush.admin.system.log.service.ISysLogQueryService;
import com.maplehush.admin.system.log.vo.SysLoginLogVO;
import com.maplehush.admin.system.log.vo.SysOperationLogVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 日志查询
 *
 * 两类日志的只读管理入口，权限分别校验
 */
@Tag(name = "日志查询")
@RestController
@RequestMapping("/api/system")
public class SysLogController {
    private final ISysLogQueryService service;

    public SysLogController(ISysLogQueryService service) {
        this.service = service;
    }

    /**
     * 查询登录、退出和刷新安全事件
     * @param parameters 分页、账号、事件、结果与时间筛选
     * @return 登录日志分页
     */
    @io.swagger.v3.oas.annotations.Parameters({
        @Parameter(name = "page", description = "页码，从 1 开始", example = "1"),
        @Parameter(name = "pageSize", description = "每页数量", example = "20"),
        @Parameter(name = "username", description = "账号，精确匹配"),
        @Parameter(name = "success", description = "结果：true 成功，false 失败"),
        @Parameter(name = "traceId", description = "请求追踪标识，精确匹配"),
        @Parameter(name = "startTime", description = "开始时间，yyyy-MM-dd HH:mm:ss"),
        @Parameter(name = "endTime", description = "结束时间，yyyy-MM-dd HH:mm:ss"),
        @Parameter(name = "sortBy", description = "排序字段：id 或 createTime", example = "createTime"),
        @Parameter(name = "sortOrder", description = "排序方向：asc 或 desc", example = "desc"),
        @Parameter(name = "eventType", description = "事件：LOGIN、LOGOUT、REFRESH", example = "LOGIN")
    })
    @Operation(summary = "分页查询登录日志")
    @GetMapping("/login-log/list")
    @PreAuthorize("hasAuthority('system:login-log:list')")
    public Result<PageResult<SysLoginLogVO>> loginLogs(
            @Parameter(hidden = true) @RequestParam Map<String, String> parameters) {
        return Result.ok(service.loginLogs(SysLogQueryDTO.parse(parameters, true)));
    }

    /**
     * 查询关键管理操作结果
     * @param parameters 分页、操作人、操作码、结果与时间筛选
     * @return 操作日志分页
     */
    @io.swagger.v3.oas.annotations.Parameters({
        @Parameter(name = "page", description = "页码，从 1 开始", example = "1"),
        @Parameter(name = "pageSize", description = "每页数量", example = "20"),
        @Parameter(name = "username", description = "账号，精确匹配"),
        @Parameter(name = "success", description = "结果：true 成功，false 失败"),
        @Parameter(name = "traceId", description = "请求追踪标识，精确匹配"),
        @Parameter(name = "startTime", description = "开始时间，yyyy-MM-dd HH:mm:ss"),
        @Parameter(name = "endTime", description = "结束时间，yyyy-MM-dd HH:mm:ss"),
        @Parameter(name = "sortBy", description = "排序字段：id 或 createTime", example = "createTime"),
        @Parameter(name = "sortOrder", description = "排序方向：asc 或 desc", example = "desc"),
        @Parameter(name = "operationCode", description = "操作码，精确匹配")
    })
    @Operation(summary = "分页查询操作日志")
    @GetMapping("/operation-log/list")
    @PreAuthorize("hasAuthority('system:operation-log:list')")
    public Result<PageResult<SysOperationLogVO>> operationLogs(
            @Parameter(hidden = true) @RequestParam Map<String, String> parameters) {
        return Result.ok(service.operationLogs(SysLogQueryDTO.parse(parameters, false)));
    }
}
