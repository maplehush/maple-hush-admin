package com.maplehush.admin.system.dept.controller;

import com.maplehush.admin.common.api.Result;
import com.maplehush.admin.system.dept.dto.SysDeptSaveDTO;
import com.maplehush.admin.system.dept.service.ISysDeptService;
import com.maplehush.admin.system.dept.support.SysDeptTree;
import com.maplehush.admin.system.dept.vo.SysDeptVO;

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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 部门管理
 *
 * 部门管理树与写接口；列表不分页
 */
@Tag(name = "部门管理")
@RestController
@RequestMapping("/api/system/dept")
public class SysDeptController {
    private final ISysDeptService service;

    public SysDeptController(ISysDeptService service) {
        this.service = service;
    }

    @Operation(summary = "查询列表部门")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:dept:list')")
    public Result<List<SysDeptVO>> list() {
        return Result.ok(service.listDepts());
    }

    @Operation(summary = "新增部门")
    @PostMapping
    @PreAuthorize("hasAuthority('system:dept:create')")
    public Result<Map<String, String>> save(@Valid @RequestBody SysDeptSaveDTO dto) {
        return Result.ok(Map.of("id", service.saveDept(dto).toString()));
    }

    @Operation(summary = "修改")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dept:update')")
    public Result<Void> update(@Parameter(description = "十进制字符串标识", example = "1") @PathVariable String id, @Valid @RequestBody SysDeptSaveDTO dto) {
        service.updateDept(SysDeptTree.id(id), dto);
        return Result.ok(null);
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dept:delete')")
    public Result<Void> remove(@Parameter(description = "十进制字符串标识", example = "1") @PathVariable String id) {
        service.removeDept(SysDeptTree.id(id));
        return Result.ok(null);
    }
}
