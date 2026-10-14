package com.maplehush.admin.system.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 首次或重置后的改密表单
 *
 * @param newPassword 用户设置的新密码，由后端统一校验强度及与原密码的差异
 */
public record InitialPasswordDTO(@NotBlank @Size(max = 128) String newPassword) {}
