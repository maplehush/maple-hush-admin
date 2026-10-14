package com.maplehush.admin.system.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 登录表单。 */
public record LoginDTO(@NotBlank @Size(max = 64) String username,
                               @NotBlank @Size(max = 128) String password,
                               @NotBlank @Size(max = 64) String captchaId,
                               @NotBlank @Size(max = 16) String captchaCode) {}
