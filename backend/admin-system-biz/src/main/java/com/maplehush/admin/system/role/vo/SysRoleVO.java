package com.maplehush.admin.system.role.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 管理角色响应；permissions 是菜单节点 ID，不是权限码。 */
public record SysRoleVO(String id, String roleCode, String name, Integer status, Boolean builtIn, String remark,
                        List<String> permissions, LocalDateTime createTime, LocalDateTime updateTime) {}
