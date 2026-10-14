package com.maplehush.admin.system.auth.vo;

import java.util.List;

/** Vben 当前用户信息，不携带版本、会话或密码。 */
public record CurrentUserVO(String userId, String username, String realName, String avatar,
                            String introduction, List<String> roles, String homePath) {}
