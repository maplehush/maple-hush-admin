package com.maplehush.admin.system.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录表单
 *
 * @param username 登录账号
 * @param password 本次登录输入的密码，不得记录日志
 * @param captchaId 后端签发的一次性验证码标识
 * @param captchaCode 用户输入的验证码答案
 */
public record LoginDTO(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 128) String password,
        @NotBlank @Size(max = 64) String captchaId,
        @NotBlank @Size(max = 16) String captchaCode) {}
