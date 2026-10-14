package com.maplehush.admin.system.auth.controller;

import com.maplehush.admin.common.api.Result;
import com.maplehush.admin.system.auth.service.IUserSnapshotService;
import com.maplehush.admin.system.auth.vo.CurrentUserVO;
import com.maplehush.admin.system.menu.vo.MenuRouteVO;
import com.maplehush.admin.system.permission.service.IPermissionService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 登录后的当前用户、权限码与可见路由查询。 */
@RestController
public class CurrentUserController {
    private final IPermissionService permissions;
    private final IUserSnapshotService snapshots;

    public CurrentUserController(IPermissionService permissions, IUserSnapshotService snapshots) {
        this.permissions = permissions; this.snapshots = snapshots;
    }

    @GetMapping("/api/user/info")
    public Result<CurrentUserVO> info() { return Result.ok(snapshots.getCurrent(permissions.current())); }

    @GetMapping("/api/auth/codes")
    public Result<List<String>> codes() { return Result.ok(permissions.current().permissions().codes().stream().sorted().toList()); }

    @GetMapping("/api/menu/all")
    public Result<List<MenuRouteVO>> menus() { return Result.ok(permissions.current().permissions().routes()); }
}
