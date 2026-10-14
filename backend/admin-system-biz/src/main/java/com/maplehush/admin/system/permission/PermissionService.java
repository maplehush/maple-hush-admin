package com.maplehush.admin.system.permission;

import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.system.auth.mapper.AuthSessionMapper;
import com.maplehush.admin.system.permission.mapper.PermissionMapper;
import com.maplehush.admin.system.user.entity.SysUser;
import java.util.List;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** 授权结果作为认证 details 保存，每个 HTTP 请求查询一次。 */
@Service
public class PermissionService {
    private final PermissionMapper mapper;
    private final AuthSessionMapper sessions;
    private final PermissionGraph graph;

    public PermissionService(PermissionMapper mapper, AuthSessionMapper sessions, PermissionGraph graph) {
        this.mapper = mapper; this.sessions = sessions; this.graph = graph;
    }

    public Context load(SysUser user) {
        return new Context(user, sessions.roleCodes(user.getId()), graph.resolve(mapper.listMenus(), mapper.listGrantedIds(user.getId())));
    }

    public Context current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getDetails() instanceof Context context)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "请重新登录");
        }
        return context;
    }

    public record Context(SysUser user, List<String> roles, PermissionGraph.Resolved permissions) {}
}
