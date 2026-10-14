package com.maplehush.admin.system.dict.controller;

import com.maplehush.admin.common.api.PageResult;
import com.maplehush.admin.common.api.Result;
import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.system.dict.dto.SysDictItemSaveDTO;
import com.maplehush.admin.system.dict.dto.SysDictQueryDTO;
import com.maplehush.admin.system.dict.dto.SysDictTypeSaveDTO;
import com.maplehush.admin.system.dict.service.ISysDictService;
import com.maplehush.admin.system.dict.vo.SysDictItemVO;
import com.maplehush.admin.system.dict.vo.SysDictOptionVO;
import com.maplehush.admin.system.dict.vo.SysDictTypeVO;

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
 * 字典管理与已登录用户选项查询；管理权限不因前端入口隐藏而省略
 */
@RestController
@RequestMapping("/api/system/dict")
public class SysDictController {
    private final ISysDictService service;

    public SysDictController(ISysDictService service) {
        this.service = service;
    }

    @GetMapping("/type/list")
    @PreAuthorize("hasAuthority('system:dict:list')")
    public Result<PageResult<SysDictTypeVO>> types(
            @RequestParam(defaultValue = "1") String page,
            @RequestParam(defaultValue = "20") String pageSize,
            @RequestParam(required = false) String dictCode,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        return Result.ok(
                service.listTypes(
                        SysDictQueryDTO.types(
                                page, pageSize, dictCode, name, status, sortBy, sortOrder)));
    }

    @GetMapping("/type/{id}")
    @PreAuthorize("hasAuthority('system:dict:list')")
    public Result<SysDictTypeVO> type(@PathVariable String id) {
        return Result.ok(service.getType(id(id)));
    }

    @PostMapping("/type")
    @PreAuthorize("hasAuthority('system:dict:create')")
    public Result<Map<String, String>> saveType(@Valid @RequestBody SysDictTypeSaveDTO dto) {
        return Result.ok(Map.of("id", service.saveType(dto).toString()));
    }

    @PutMapping("/type/{id}")
    @PreAuthorize("hasAuthority('system:dict:update')")
    public Result<Void> updateType(
            @PathVariable String id, @Valid @RequestBody SysDictTypeSaveDTO dto) {
        service.updateType(id(id), dto);
        return Result.ok(null);
    }

    @DeleteMapping("/type/{id}")
    @PreAuthorize("hasAuthority('system:dict:delete')")
    public Result<Void> removeType(@PathVariable String id) {
        service.removeType(id(id));
        return Result.ok(null);
    }

    @GetMapping("/item/list")
    @PreAuthorize("hasAuthority('system:dict:list')")
    public Result<PageResult<SysDictItemVO>> items(
            @RequestParam(defaultValue = "1") String page,
            @RequestParam(defaultValue = "20") String pageSize,
            @RequestParam String dictTypeId,
            @RequestParam(required = false) String label,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        return Result.ok(
                service.listItems(
                        SysDictQueryDTO.items(
                                page, pageSize, dictTypeId, label, status, sortBy, sortOrder)));
    }

    @GetMapping("/item/{id}")
    @PreAuthorize("hasAuthority('system:dict:list')")
    public Result<SysDictItemVO> item(@PathVariable String id) {
        return Result.ok(service.getItem(id(id)));
    }

    @PostMapping("/item")
    @PreAuthorize("hasAuthority('system:dict:create')")
    public Result<Map<String, String>> saveItem(@Valid @RequestBody SysDictItemSaveDTO dto) {
        return Result.ok(Map.of("id", service.saveItem(dto).toString()));
    }

    @PutMapping("/item/{id}")
    @PreAuthorize("hasAuthority('system:dict:update')")
    public Result<Void> updateItem(
            @PathVariable String id, @Valid @RequestBody SysDictItemSaveDTO dto) {
        service.updateItem(id(id), dto);
        return Result.ok(null);
    }

    @DeleteMapping("/item/{id}")
    @PreAuthorize("hasAuthority('system:dict:delete')")
    public Result<Void> removeItem(@PathVariable String id) {
        service.removeItem(id(id));
        return Result.ok(null);
    }

    @GetMapping("/type/{dictCode}/items")
    @PreAuthorize("isAuthenticated()")
    public Result<List<SysDictOptionVO>> options(@PathVariable String dictCode) {
        return Result.ok(service.listOptions(dictCode));
    }

    private Long id(String value) {
        try {
            if (!value.matches("[1-9][0-9]{0,18}")) {
                throw new IllegalArgumentException();
            }
            return Long.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "字典 ID 无效");
        }
    }
}
