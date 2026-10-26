package com.maplehush.admin.system.user.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建用户表单；密码由后端生成临时哈希，不接受密码、任意头像 URL 或权限码输入
 *
 * @param username 登录账号，至多 64 字符，创建后不可修改
 * @param name 管理页姓名，映射 real_name
 * @param deptId 所属部门 ID；空值或 0 表示未分配
 * @param roleIds 必须分配的普通角色 ID，至少一个
 * @param status 1 启用、0 停用；缺省为 1
 * @param avatarUploadId 可选的当前操作人上传标识；省略/null 保留原头像
 * @param remark 管理员备注，与本人简介分开保存
 */
public record SysUserCreateDTO(
        @NotBlank @Size(max = 64) @Schema(description = "登录账号，至多 64 字符，创建后不可修改") String username,
        @NotBlank @Size(max = 100) @Schema(description = "管理页姓名，映射 real_name") String name,
        @Size(max = 19) @Schema(description = "所属部门 ID；空值或 0 表示未分配", example = "1") String deptId,
        @NotEmpty @Size(max = 100) @Schema(description = "必须分配的普通角色 ID，至少一个") List<@NotBlank String> roleIds,
        @Min(0) @Max(1) @Schema(description = "1 启用、0 停用；缺省为 1", allowableValues = {"0", "1"}, example = "1") Integer status,
        @Size(max = 500) @Schema(description = "管理员备注，与本人简介分开保存") String remark,
        @Pattern(regexp = "[a-f0-9]{32}") @Schema(description = "可选的当前操作人上传标识；省略/null 保留原头像") String avatarUploadId) {
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
