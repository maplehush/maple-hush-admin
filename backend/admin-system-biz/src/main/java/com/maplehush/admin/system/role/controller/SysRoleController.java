package com.maplehush.admin.system.role.controller;

import com.maplehush.admin.common.api.PageResult;
import com.maplehush.admin.common.api.Result;
import com.maplehush.admin.system.role.dto.SysRoleQueryDTO;
import com.maplehush.admin.system.role.dto.SysRoleSaveDTO;
import com.maplehush.admin.system.role.dto.SysRoleStatusDTO;
import com.maplehush.admin.system.role.service.ISysRoleService;
import com.maplehush.admin.system.role.support.SysRoleGrantRules;
import com.maplehush.admin.system.role.vo.SysRoleVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
 * 角色管理
 *
 * 角色管理接口；授权作为创建/修改的可选字段，由 Service 额外检查 grant 权限
 */
@Tag(name = "角色管理")
@RestController
@RequestMapping("/api/system/role")
public class SysRoleController {
    private final ISysRoleService service;

    public SysRoleController(ISysRoleService service) {
        this.service = service;
    }

    @Operation(summary = "查询列表角色")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:role:list')")
    public Result<PageResult<SysRoleVO>> list(
            @Parameter(description = "页码，从 1 开始", example = "1") @RequestParam(defaultValue = "1") String page,
            @Parameter(description = "每页数量", example = "20") @RequestParam(defaultValue = "20") String pageSize,
            @Parameter(description = "名称筛选") @RequestParam(required = false) String name,
            @Parameter(description = "状态：0 禁用，1 启用", example = "1") @RequestParam(required = false) String status,
            @Parameter(description = "开始时间，yyyy-MM-dd HH:mm:ss") @RequestParam(required = false) String startTime,
            @Parameter(description = "结束时间，yyyy-MM-dd HH:mm:ss") @RequestParam(required = false) String endTime) {
        return Result.ok(
                service.listRoles(
                        SysRoleQueryDTO.parse(page, pageSize, name, status, startTime, endTime)));
    }

    @Operation(summary = "查询详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:list')")
    public Result<SysRoleVO> get(@Parameter(description = "十进制字符串标识", example = "1") @PathVariable String id) {
        return Result.ok(service.getRole(SysRoleGrantRules.id(id)));
    }

    @Operation(summary = "新增角色")
    @PostMapping
    @PreAuthorize("hasAuthority('system:role:create')")
    public Result<Map<String, String>> save(@Valid @RequestBody SysRoleSaveDTO dto) {
        return Result.ok(Map.of("id", service.saveRole(dto).toString()));
    }

    @Operation(summary = "修改")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:update')")
    public Result<Void> update(@Parameter(description = "十进制字符串标识", example = "1") @PathVariable String id, @Valid @RequestBody SysRoleSaveDTO dto) {
        service.updateRole(SysRoleGrantRules.id(id), dto);
        return Result.ok(null);
    }

    @Operation(summary = "修改状态")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:role:update')")
    public Result<Void> updateStatus(
            @Parameter(description = "十进制字符串标识", example = "1") @PathVariable String id, @Valid @RequestBody SysRoleStatusDTO dto) {
        service.updateRoleStatus(SysRoleGrantRules.id(id), dto.status());
        return Result.ok(null);
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:delete')")
    public Result<Void> remove(@Parameter(description = "十进制字符串标识", example = "1") @PathVariable String id) {
        service.removeRole(SysRoleGrantRules.id(id));
        return Result.ok(null);
    }
}
