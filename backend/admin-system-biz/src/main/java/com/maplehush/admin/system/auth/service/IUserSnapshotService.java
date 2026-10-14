package com.maplehush.admin.system.auth.service;

import com.maplehush.admin.system.auth.vo.CurrentUserVO;
import com.maplehush.admin.system.permission.service.IPermissionService;
import com.maplehush.admin.system.user.entity.SysUser;

/**
 * 当前用户快照写入与查询
 */
public interface IUserSnapshotService {
    /**
     * 保存用户的最小信息快照
     *
     * @param user 已从数据库读取的用户状态和授权版本
     */
    void write(SysUser user);

    /**
     * 校验快照版本并组装当前用户信息
     *
     * @param context 当前请求的用户、角色和有效权限上下文
     * @return 不包含密码或令牌的当前用户信息
     */
    CurrentUserVO getCurrent(IPermissionService.Context context);
}
