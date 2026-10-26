package com.maplehush.admin.system.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 独立角色状态修改请求
 *
 * @param status 启用状态：1 启用，0 停用
 */
public record SysRoleStatusDTO(@NotNull @Min(0) @Max(1) @Schema(description = "启用状态：1 启用，0 停用", allowableValues = {"0", "1"}, example = "1") Integer status) {}
