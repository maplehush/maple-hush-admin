package com.maplehush.admin.system.user.vo;

/**
 * 本人资料更新结果
 * @param userId 当前用户 ID 字符串
 * @param realName 姓名
 * @param introduction 可空个人简介，不是管理员备注
 */
public record UserProfileVO(String userId, String realName, String introduction) {}
