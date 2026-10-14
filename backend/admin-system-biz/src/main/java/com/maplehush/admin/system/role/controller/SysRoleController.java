package com.maplehush.admin.system.role.controller;

import com.maplehush.admin.common.api.PageResult;
import com.maplehush.admin.common.api.Result;
import com.maplehush.admin.system.role.dto.SysRoleQueryDTO;
import com.maplehush.admin.system.role.dto.SysRoleSaveDTO;
import com.maplehush.admin.system.role.service.SysRoleService;
import com.maplehush.admin.system.role.support.SysRoleGrantRules;
import com.maplehush.admin.system.role.vo.SysRoleVO;
import jakarta.validation.Valid;
import java.util.Map;
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

/** 角色管理接口；授权作为创建/修改的可选字段，由 Service 额外检查 grant 权限。 */
@RestController
@RequestMapping("/api/system/role")
public class SysRoleController {
    private final SysRoleService service;
    public SysRoleController(SysRoleService service) { this.service = service; }

    @GetMapping("/list") @PreAuthorize("hasAuthority('system:role:list')")
    public Result<PageResult<SysRoleVO>> list(@RequestParam(defaultValue = "1") String page,
            @RequestParam(defaultValue = "20") String pageSize, @RequestParam(required = false) String name,
            @RequestParam(required = false) String status, @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return Result.ok(service.listRoles(SysRoleQueryDTO.parse(page, pageSize, name, status, startTime, endTime)));
    }

    @GetMapping("/{id}") @PreAuthorize("hasAuthority('system:role:list')")
    public Result<SysRoleVO> get(@PathVariable String id) { return Result.ok(service.getRole(SysRoleGrantRules.id(id))); }

    @PostMapping @PreAuthorize("hasAuthority('system:role:create')")
    public Result<Map<String, String>> save(@Valid @RequestBody SysRoleSaveDTO dto) {
        return Result.ok(Map.of("id", service.saveRole(dto).toString()));
    }

    @PutMapping("/{id}") @PreAuthorize("hasAuthority('system:role:update')")
    public Result<Void> update(@PathVariable String id, @Valid @RequestBody SysRoleSaveDTO dto) {
        service.updateRole(SysRoleGrantRules.id(id), dto); return Result.ok(null);
    }

    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('system:role:delete')")
    public Result<Void> remove(@PathVariable String id) { service.removeRole(SysRoleGrantRules.id(id)); return Result.ok(null); }
}
