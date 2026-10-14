package com.maplehush.admin.system.dept.controller;

import com.maplehush.admin.common.api.Result;
import com.maplehush.admin.system.dept.dto.SysDeptSaveDTO;
import com.maplehush.admin.system.dept.service.ISysDeptService;
import com.maplehush.admin.system.dept.support.SysDeptTree;
import com.maplehush.admin.system.dept.vo.SysDeptVO;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 部门管理树与写接口；列表不分页。 */
@RestController
@RequestMapping("/api/system/dept")
public class SysDeptController {
    private final ISysDeptService service;
    public SysDeptController(ISysDeptService service) { this.service = service; }

    @GetMapping("/list") @PreAuthorize("hasAuthority('system:dept:list')")
    public Result<List<SysDeptVO>> list() { return Result.ok(service.listDepts()); }

    @PostMapping @PreAuthorize("hasAuthority('system:dept:create')")
    public Result<Map<String, String>> save(@Valid @RequestBody SysDeptSaveDTO dto) {
        return Result.ok(Map.of("id", service.saveDept(dto).toString()));
    }

    @PutMapping("/{id}") @PreAuthorize("hasAuthority('system:dept:update')")
    public Result<Void> update(@PathVariable String id, @Valid @RequestBody SysDeptSaveDTO dto) {
        service.updateDept(SysDeptTree.id(id), dto); return Result.ok(null);
    }

    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('system:dept:delete')")
    public Result<Void> remove(@PathVariable String id) { service.removeDept(SysDeptTree.id(id)); return Result.ok(null); }
}
