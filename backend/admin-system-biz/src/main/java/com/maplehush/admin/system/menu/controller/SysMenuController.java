package com.maplehush.admin.system.menu.controller;

import com.maplehush.admin.common.api.Result;
import com.maplehush.admin.system.menu.dto.SysMenuSaveDTO;
import com.maplehush.admin.system.menu.service.ISysMenuService;
import com.maplehush.admin.system.menu.support.SysMenuRules;
import com.maplehush.admin.system.menu.vo.SysMenuVO;

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

import java.util.List;
import java.util.Map;

/**
 * 菜单管理
 *
 * 菜单管理；按钮可见性不能替代这些接口授权声明
 */
@Tag(name = "菜单管理")
@RestController
@RequestMapping("/api/system/menu")
public class SysMenuController {
    private final ISysMenuService service;

    public SysMenuController(ISysMenuService service) {
        this.service = service;
    }

    @Operation(summary = "查询列表菜单")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:menu:list')")
    public Result<List<SysMenuVO>> list() {
        return Result.ok(service.listMenus());
    }

    @Operation(summary = "检查菜单名称是否存在")
    @GetMapping("/name-exists")
    @PreAuthorize("hasAuthority('system:menu:list')")
    public Result<Boolean> nameExists(
            @Parameter(description = "名称筛选") @RequestParam String name, @Parameter(description = "十进制字符串标识", example = "1") @RequestParam(required = false) String id) {
        return Result.ok(service.nameExists(name, id == null ? null : SysMenuRules.id(id)));
    }

    @Operation(summary = "检查路由地址是否存在")
    @GetMapping("/path-exists")
    @PreAuthorize("hasAuthority('system:menu:list')")
    public Result<Boolean> pathExists(
            @Parameter(description = "完整静态路由地址", example = "/system/user") @RequestParam String path, @Parameter(description = "十进制字符串标识", example = "1") @RequestParam(required = false) String id) {
        return Result.ok(service.pathExists(path, id == null ? null : SysMenuRules.id(id)));
    }

    @Operation(summary = "新增菜单")
    @PostMapping
    @PreAuthorize("hasAuthority('system:menu:create')")
    public Result<Map<String, String>> save(@Valid @RequestBody SysMenuSaveDTO dto) {
        return Result.ok(Map.of("id", service.saveMenu(dto).toString()));
    }

    @Operation(summary = "修改")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:menu:update')")
    public Result<Void> update(@Parameter(description = "十进制字符串标识", example = "1") @PathVariable String id, @Valid @RequestBody SysMenuSaveDTO dto) {
        service.updateMenu(SysMenuRules.id(id), dto);
        return Result.ok(null);
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:menu:delete')")
    public Result<Void> remove(@Parameter(description = "十进制字符串标识", example = "1") @PathVariable String id) {
        service.removeMenu(SysMenuRules.id(id));
        return Result.ok(null);
    }
}
