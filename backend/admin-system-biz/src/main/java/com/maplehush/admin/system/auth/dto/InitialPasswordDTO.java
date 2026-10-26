package com.maplehush.admin.system.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 首次或重置后的改密表单
 *
 * @param newPassword 用户设置的新密码，由后端统一校验强度及与原密码的差异
 */
public record InitialPasswordDTO(@NotBlank @Size(max = 128) @Schema(description = "用户设置的新密码，由后端统一校验强度及与原密码的差异", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY) String newPassword) {}
