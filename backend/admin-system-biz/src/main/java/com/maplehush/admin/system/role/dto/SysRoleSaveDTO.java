package com.maplehush.admin.system.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 角色表单；创建时要求 roleCode，修改时只允许回传原编码。null permissions 表示保留
 *
 * @param roleCode 稳定角色编码；创建时必填，修改时不能变更
 * @param name 业务显示名称
 * @param status 启用状态：1 启用，0 停用；表单为空时按创建或修改规则处理
 * @param remark 业务备注
 * @param permissions 授予的菜单节点 ID；修改时 null 保留原授权，空列表清空授权
 */
public record SysRoleSaveDTO(
        @Pattern(regexp = "[a-z][a-z0-9_]{0,63}") @Schema(description = "稳定角色编码；创建时必填，修改时不能变更") String roleCode,
        @NotBlank @Size(max = 100) @Schema(description = "业务显示名称") String name,
        @Min(0) @Max(1) @Schema(description = "启用状态：1 启用，0 停用；表单为空时按创建或修改规则处理", allowableValues = {"0", "1"}, example = "1") Integer status,
        @Size(max = 500) @Schema(description = "业务备注") String remark,
        @Size(max = 100) @Schema(description = "授予的菜单节点 ID；修改时 null 保留原授权，空列表清空授权") List<String> permissions) {}
