package com.maplehush.admin.system.user.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 用户资料全量表单；角色和密码通过独立接口修改
 *
 * @param name 用户姓名，映射 real_name，不修改登录账号
 * @param deptId 部门 ID；空值或 0 清除部门关联
 * @param status 启用状态；省略或 null 保留原状态
 * @param avatarUploadId 可选的当前操作人上传标识；省略/null 保留原头像
 * @param remark 管理员备注；空值清除原备注，不修改 introduction
 */
public record SysUserUpdateDTO(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 19) String deptId,
        @Min(0) @Max(1) Integer status,
        @Size(max = 500) String remark,
        @Pattern(regexp = "[a-f0-9]{32}") String avatarUploadId) {
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
