package com.maplehush.admin.system.user.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 用户角色全量替换表单
 *
 * @param roleIds 普通角色 ID，至多 100 个；新增必须启用，原已关联停用角色可保留；空列表清空，null 拒绝
 */
public record SysUserRolesDTO(@NotNull @Size(max = 100) @Schema(description = "普通角色 ID，至多 100 个；新增必须启用，原已关联停用角色可保留；空列表清空，null 拒绝") List<@NotBlank String> roleIds) {
    /**
     * 拒绝表单契约之外的字段，避免敏感字段被静默忽略后误认为已修改
     *
     * @param field 未知字段名
     * @param ignored 未知字段值，不写入异常或日志
     */
    @JsonAnySetter
    public void rejectUnknown(String field, Object ignored) {
        throw new IllegalArgumentException("用户表单包含不支持的字段");
    }
}
