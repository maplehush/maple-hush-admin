package com.maplehush.admin.system.auth.vo;

import java.util.List;

/**
 * Vben 当前用户信息，不携带版本、会话或密码
 *
 * @param userId 当前用户 ID，以字符串返回
 * @param username 登录账号
 * @param realName 用户真实姓名
 * @param avatar 头像长期 HTTPS 地址
 * @param introduction 用户个人简介
 * @param roles 当前有效角色编码列表，不包含被停用角色
 * @param homePath 当前用户可访问的默认首页路径
 */
public record CurrentUserVO(
        String userId,
        String username,
        String realName,
        String avatar,
        String introduction,
        List<String> roles,
        String homePath) {}
