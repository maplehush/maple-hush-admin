package com.maplehush.admin.system.auth.service;

import com.maplehush.admin.system.auth.vo.CurrentUserVO;
import com.maplehush.admin.system.permission.service.IPermissionService;
import com.maplehush.admin.system.user.entity.SysUser;

/** 当前用户快照写入与查询。 */
public interface IUserSnapshotService {
    /** 保存用户的最小信息快照。 */
    void write(SysUser user);

    /** 校验快照版本并组装当前用户信息。 */
    CurrentUserVO getCurrent(IPermissionService.Context context);
}
