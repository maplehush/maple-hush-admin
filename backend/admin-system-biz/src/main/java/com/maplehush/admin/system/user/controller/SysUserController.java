package com.maplehush.admin.system.user.controller;

import com.maplehush.admin.common.api.PageResult;
import com.maplehush.admin.common.api.Result;
import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.system.user.dto.SysUserCreateDTO;
import com.maplehush.admin.system.user.dto.SysUserQueryDTO;
import com.maplehush.admin.system.user.dto.SysUserRolesDTO;
import com.maplehush.admin.system.user.dto.SysUserUpdateDTO;
import com.maplehush.admin.system.user.service.ISysUserService;
import com.maplehush.admin.system.user.vo.SysUserVO;

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
 * 用户管理 HTTP 接口；本人资料、头像和旧密码改密由后续个人中心接口提供
 */
@RestController
@RequestMapping("/api/system/user")
public class SysUserController {
    private final ISysUserService service;

    public SysUserController(ISysUserService service) {
        this.service = service;
    }

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:user:list')")
    public Result<PageResult<SysUserVO>> list(
            @RequestParam(defaultValue = "1") String page,
            @RequestParam(defaultValue = "20") String pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String deptId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return Result.ok(
                service.listUsers(
                        SysUserQueryDTO.parse(
                                page, pageSize, name, deptId, status, startTime, endTime)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:list')")
    public Result<SysUserVO> get(@PathVariable String id) {
        return Result.ok(service.getUser(id(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:user:create')")
    public Result<Map<String, String>> save(@Valid @RequestBody SysUserCreateDTO dto) {
        return Result.ok(Map.of("id", service.saveUser(dto).toString()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:update')")
    public Result<Void> update(@PathVariable String id, @Valid @RequestBody SysUserUpdateDTO dto) {
        service.updateUser(id(id), dto);
        return Result.ok(null);
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('system:user:assign-role')")
    public Result<Void> assign(@PathVariable String id, @Valid @RequestBody SysUserRolesDTO dto) {
        service.assignRoles(id(id), dto);
        return Result.ok(null);
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('system:user:reset-password')")
    public Result<Void> reset(@PathVariable String id) {
        service.resetPassword(id(id));
        return Result.ok(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:delete')")
    public Result<Void> remove(@PathVariable String id) {
        service.removeUser(id(id));
        return Result.ok(null);
    }

    private Long id(String value) {
        try {
            if (!value.matches("[1-9][0-9]{0,18}")) {
                throw new IllegalArgumentException();
            }
            return Long.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "用户 ID 无效");
        }
    }
}
