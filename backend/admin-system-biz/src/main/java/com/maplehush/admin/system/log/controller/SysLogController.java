package com.maplehush.admin.system.log.controller;

import com.maplehush.admin.common.api.PageResult;
import com.maplehush.admin.common.api.Result;
import com.maplehush.admin.system.log.dto.SysLogQueryDTO;
import com.maplehush.admin.system.log.service.ISysLogQueryService;
import com.maplehush.admin.system.log.vo.SysLoginLogVO;
import com.maplehush.admin.system.log.vo.SysOperationLogVO;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 两类日志的只读管理入口，权限分别校验
 */
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
    @GetMapping("/login-log/list")
    @PreAuthorize("hasAuthority('system:login-log:list')")
    public Result<PageResult<SysLoginLogVO>> loginLogs(
            @RequestParam Map<String, String> parameters) {
        return Result.ok(service.loginLogs(SysLogQueryDTO.parse(parameters, true)));
    }

    /**
     * 查询关键管理操作结果
     * @param parameters 分页、操作人、操作码、结果与时间筛选
     * @return 操作日志分页
     */
    @GetMapping("/operation-log/list")
    @PreAuthorize("hasAuthority('system:operation-log:list')")
    public Result<PageResult<SysOperationLogVO>> operationLogs(
            @RequestParam Map<String, String> parameters) {
        return Result.ok(service.operationLogs(SysLogQueryDTO.parse(parameters, false)));
    }
}
