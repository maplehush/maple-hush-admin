package com.maplehush.admin.system.permission.service.impl;

import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.system.auth.mapper.AuthSessionMapper;
import com.maplehush.admin.system.permission.mapper.PermissionMapper;
import com.maplehush.admin.system.permission.service.IPermissionService;
import com.maplehush.admin.system.permission.support.PermissionGraph;
import com.maplehush.admin.system.user.entity.SysUser;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** 授权结果作为认证 details 保存，每个 HTTP 请求查询一次。 */
@Service
public class PermissionServiceImpl implements IPermissionService {
    private final PermissionMapper mapper;
    private final AuthSessionMapper sessions;
    private final PermissionGraph graph;

    public PermissionServiceImpl(PermissionMapper mapper, AuthSessionMapper sessions, PermissionGraph graph) {
        this.mapper = mapper; this.sessions = sessions; this.graph = graph;
    }

    @Override
    public Context load(SysUser user) {
        return new Context(user, sessions.roleCodes(user.getId()), graph.resolve(mapper.listMenus(), mapper.listGrantedIds(user.getId())));
    }

    @Override
    public Context current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getDetails() instanceof Context context)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "请重新登录");
        }
        return context;
    }
}
