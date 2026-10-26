package com.maplehush.admin.system.user.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 本人改密请求，不记录或回显明文
 *
 * @param oldPassword 当前密码，不做 trim
 * @param newPassword 新密码，后端额外验证 BCrypt 字节上限及常见密码
 */
public record UserPasswordDTO(
        @NotBlank @Size(max = 128) @Schema(description = "当前密码，不做 trim", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY) String oldPassword,
        @NotBlank @Size(min = 8, max = 128) @Schema(description = "新密码，后端额外验证 BCrypt 字节上限及常见密码", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY) String newPassword) {
    /**
     * 拒绝未知字段，不能通过本人接口修改账号、角色、状态或头像 URL
     * @param field 未知字段名称
     * @param ignored 未知字段值，不进入异常或日志
     */
    @JsonAnySetter
    public void rejectUnknown(String field, Object ignored) {
        throw new IllegalArgumentException("本人表单包含不支持的字段");
    }
}
