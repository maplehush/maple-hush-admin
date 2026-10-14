package com.maplehush.admin.system.permission.service;

import com.maplehush.admin.system.permission.support.PermissionGraph;
import com.maplehush.admin.system.user.entity.SysUser;
import java.util.List;

/** 当前用户与有效权限查询。 */
public interface IPermissionService {
    /** 读取用户的角色和有效权限。 */
    Context load(SysUser user);

    /** 获取当前请求的用户与有效权限。 */
    Context current();

    record Context(SysUser user, List<String> roles, PermissionGraph.Resolved permissions) {}
}
