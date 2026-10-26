package com.maplehush.admin.system.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 本人资料更新结果
 * @param userId 当前用户 ID 字符串
 * @param realName 姓名
 * @param introduction 可空个人简介，不是管理员备注
 */
public record UserProfileVO(@Schema(description = "可空个人简介，不是管理员备注", example = "1") String userId, @Schema(description = "真实姓名") String realName, @Schema(description = "个人简介") String introduction) {}
