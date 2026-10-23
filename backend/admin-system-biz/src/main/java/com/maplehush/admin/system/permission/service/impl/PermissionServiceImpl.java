package com.maplehush.admin.system.permission.service.impl;

import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.system.permission.mapper.PermissionMapper;
import com.maplehush.admin.system.permission.service.IPermissionService;
import com.maplehush.admin.system.permission.support.PermissionGraph;
import com.maplehush.admin.system.user.entity.SysUser;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 授权结果作为认证 details 保存，每个 HTTP 请求查询一次
 */
@Service
public class PermissionServiceImpl implements IPermissionService {
    private final PermissionMapper mapper;
    private final PermissionGraph graph;

    public PermissionServiceImpl(PermissionMapper mapper, PermissionGraph graph) {
        this.mapper = mapper;
        this.graph = graph;
    }

    @Override
    public Context load(SysUser user) {
        var roles = new LinkedHashSet<String>();
        var grants = new HashSet<Long>();
        for (var row : mapper.listRoleGrants(user.getId())) {
            roles.add(row.roleCode());
            if (row.menuId() != null) {
                grants.add(row.menuId());
            }
        }
        return new Context(
                user,
                List.copyOf(roles),
                graph.resolve(mapper.listMenus(), grants));
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
