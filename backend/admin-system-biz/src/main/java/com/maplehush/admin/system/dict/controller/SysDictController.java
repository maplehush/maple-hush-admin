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
 * 字典管理
 *
 * 字典管理与已登录用户选项查询；管理权限不因前端入口隐藏而省略
 */
@Tag(name = "字典管理")
@RestController
@RequestMapping("/api/system/dict")
public class SysDictController {
    private final ISysDictService service;

    public SysDictController(ISysDictService service) {
        this.service = service;
    }

    @Operation(summary = "分页查询字典类型")
    @GetMapping("/type/list")
    @PreAuthorize("hasAuthority('system:dict:list')")
    public Result<PageResult<SysDictTypeVO>> types(
            @Parameter(description = "页码，从 1 开始", example = "1") @RequestParam(defaultValue = "1") String page,
            @Parameter(description = "每页数量", example = "20") @RequestParam(defaultValue = "20") String pageSize,
            @Parameter(description = "字典编码", example = "status") @RequestParam(required = false) String dictCode,
            @Parameter(description = "名称筛选") @RequestParam(required = false) String name,
            @Parameter(description = "状态：0 禁用，1 启用", example = "1") @RequestParam(required = false) String status,
            @Parameter(description = "排序字段，参考接口说明", example = "createTime") @RequestParam(required = false) String sortBy,
            @Parameter(description = "排序方向：asc 或 desc", example = "desc") @RequestParam(required = false) String sortOrder) {
        return Result.ok(
                service.listTypes(
                        SysDictQueryDTO.types(
                                page, pageSize, dictCode, name, status, sortBy, sortOrder)));
    }

    @Operation(summary = "查询字典类型详情")
    @GetMapping("/type/{id}")
    @PreAuthorize("hasAuthority('system:dict:list')")
    public Result<SysDictTypeVO> type(@Parameter(description = "十进制字符串标识", example = "1") @PathVariable String id) {
        return Result.ok(service.getType(id(id)));
    }

    @Operation(summary = "新增字典类型")
    @PostMapping("/type")
    @PreAuthorize("hasAuthority('system:dict:create')")
    public Result<Map<String, String>> saveType(@Valid @RequestBody SysDictTypeSaveDTO dto) {
        return Result.ok(Map.of("id", service.saveType(dto).toString()));
    }

    @Operation(summary = "修改字典类型")
    @PutMapping("/type/{id}")
    @PreAuthorize("hasAuthority('system:dict:update')")
    public Result<Void> updateType(
            @Parameter(description = "十进制字符串标识", example = "1") @PathVariable String id, @Valid @RequestBody SysDictTypeSaveDTO dto) {
        service.updateType(id(id), dto);
        return Result.ok(null);
    }

    @Operation(summary = "删除字典类型")
    @DeleteMapping("/type/{id}")
    @PreAuthorize("hasAuthority('system:dict:delete')")
    public Result<Void> removeType(@Parameter(description = "十进制字符串标识", example = "1") @PathVariable String id) {
        service.removeType(id(id));
        return Result.ok(null);
    }

    @Operation(summary = "分页查询字典项")
    @GetMapping("/item/list")
    @PreAuthorize("hasAuthority('system:dict:list')")
    public Result<PageResult<SysDictItemVO>> items(
            @Parameter(description = "页码，从 1 开始", example = "1") @RequestParam(defaultValue = "1") String page,
            @Parameter(description = "每页数量", example = "20") @RequestParam(defaultValue = "20") String pageSize,
            @Parameter(description = "字典类型标识", example = "1") @RequestParam String dictTypeId,
            @Parameter(description = "显示文本") @RequestParam(required = false) String label,
            @Parameter(description = "状态：0 禁用，1 启用", example = "1") @RequestParam(required = false) String status,
            @Parameter(description = "排序字段，参考接口说明", example = "createTime") @RequestParam(required = false) String sortBy,
            @Parameter(description = "排序方向：asc 或 desc", example = "desc") @RequestParam(required = false) String sortOrder) {
        return Result.ok(
                service.listItems(
                        SysDictQueryDTO.items(
                                page, pageSize, dictTypeId, label, status, sortBy, sortOrder)));
    }

    @Operation(summary = "查询字典项详情")
    @GetMapping("/item/{id}")
    @PreAuthorize("hasAuthority('system:dict:list')")
    public Result<SysDictItemVO> item(@Parameter(description = "十进制字符串标识", example = "1") @PathVariable String id) {
        return Result.ok(service.getItem(id(id)));
    }

    @Operation(summary = "新增字典项")
    @PostMapping("/item")
    @PreAuthorize("hasAuthority('system:dict:create')")
    public Result<Map<String, String>> saveItem(@Valid @RequestBody SysDictItemSaveDTO dto) {
        return Result.ok(Map.of("id", service.saveItem(dto).toString()));
    }

    @Operation(summary = "修改字典项")
    @PutMapping("/item/{id}")
    @PreAuthorize("hasAuthority('system:dict:update')")
    public Result<Void> updateItem(
            @Parameter(description = "十进制字符串标识", example = "1") @PathVariable String id, @Valid @RequestBody SysDictItemSaveDTO dto) {
        service.updateItem(id(id), dto);
        return Result.ok(null);
    }

    @Operation(summary = "删除字典项")
    @DeleteMapping("/item/{id}")
    @PreAuthorize("hasAuthority('system:dict:delete')")
    public Result<Void> removeItem(@Parameter(description = "十进制字符串标识", example = "1") @PathVariable String id) {
        service.removeItem(id(id));
        return Result.ok(null);
    }

    @Operation(summary = "查询启用的字典选项")
    @GetMapping("/type/{dictCode}/items")
    @PreAuthorize("isAuthenticated()")
    public Result<List<SysDictOptionVO>> options(@Parameter(description = "字典编码", example = "status") @PathVariable String dictCode) {
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
