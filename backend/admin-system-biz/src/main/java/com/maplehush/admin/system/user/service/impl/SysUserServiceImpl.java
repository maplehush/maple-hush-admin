package com.maplehush.admin.system.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.maplehush.admin.common.annotation.OperationLog;
import com.maplehush.admin.common.api.PageResult;
import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.system.auth.support.PasswordPolicy;
import com.maplehush.admin.system.dept.mapper.SysDeptMapper;
import com.maplehush.admin.system.file.service.IFileUploadService;
import com.maplehush.admin.system.menu.vo.MenuRouteVO;
import com.maplehush.admin.system.permission.mapper.PermissionMapper;
import com.maplehush.admin.system.permission.service.IAuthorizationChangeService;
import com.maplehush.admin.system.permission.service.IPermissionService;
import com.maplehush.admin.system.role.mapper.SysRoleMapper;
import com.maplehush.admin.system.role.mapper.SysRoleMenuMapper;
import com.maplehush.admin.system.user.dto.SysUserCreateDTO;
import com.maplehush.admin.system.user.dto.SysUserQueryDTO;
import com.maplehush.admin.system.user.dto.SysUserRolesDTO;
import com.maplehush.admin.system.user.dto.SysUserUpdateDTO;
import com.maplehush.admin.system.user.entity.SysUser;
import com.maplehush.admin.system.user.mapper.SysUserMapper;
import com.maplehush.admin.system.user.mapper.SysUserRoleMapper;
import com.maplehush.admin.system.user.service.ISysUserService;
import com.maplehush.admin.system.user.vo.SysUserVO;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户资料、角色与会话变更的事务边界；不通过用户名或固定 ID 判断唯一超管
 */
@Service
public class SysUserServiceImpl implements ISysUserService {
    private final SysUserMapper mapper;
    private final SysUserRoleMapper relations;
    private final SysRoleMapper roles;
    private final SysRoleMenuMapper grants;
    private final SysDeptMapper departments;
    private final PermissionMapper permissionMapper;
    private final IPermissionService permissions;
    private final IAuthorizationChangeService changes;
    private final PasswordEncoder passwords;
    private final IFileUploadService files;

    public SysUserServiceImpl(
            SysUserMapper mapper,
            SysUserRoleMapper relations,
            SysRoleMapper roles,
            SysRoleMenuMapper grants,
            SysDeptMapper departments,
            PermissionMapper permissionMapper,
            IPermissionService permissions,
            IAuthorizationChangeService changes,
            PasswordEncoder passwords,
            IFileUploadService files) {
        this.mapper = mapper;
        this.relations = relations;
        this.roles = roles;
        this.grants = grants;
        this.departments = departments;
        this.permissionMapper = permissionMapper;
        this.permissions = permissions;
        this.changes = changes;
        this.passwords = passwords;
        this.files = files;
    }

    @Transactional(readOnly = true)
    @Override
    public PageResult<SysUserVO> listUsers(SysUserQueryDTO query) {
        // 显式安全投影，实体的密码字段也配置为默认不可查询。
        var wrapper =
                Wrappers.<SysUser>lambdaQuery()
                        .select(
                                SysUser::getId,
                                SysUser::getUsername,
                                SysUser::getAvatarUrl,
                                SysUser::getRealName,
                                SysUser::getDeptId,
                                SysUser::getStatus,
                                SysUser::getRemark,
                                SysUser::getCreateTime,
                                SysUser::getUpdateTime)
                        .like(query.name() != null, SysUser::getRealName, query.name())
                        .eq(query.deptId() != null, SysUser::getDeptId, query.deptId())
                        .eq(query.status() != null, SysUser::getStatus, query.status())
                        .ge(query.startTime() != null, SysUser::getCreateTime, query.startTime())
                        .le(query.endTime() != null, SysUser::getCreateTime, query.endTime())
                        .orderByAsc(SysUser::getId);
        var page = mapper.selectPage(new Page<SysUser>(query.page(), query.pageSize()), wrapper);
        var memberships = memberships(page.getRecords().stream().map(SysUser::getId).toList());
        return new PageResult<>(
                page.getRecords().stream()
                        .map(user -> vo(user, memberships.getOrDefault(user.getId(), Set.of())))
                        .toList(),
                page.getTotal());
    }

    @Transactional(readOnly = true)
    @Override
    public SysUserVO getUser(Long id) {
        SysUser user = mapper.selectById(id);
        if (user == null) {
            throw notFound();
        }
        return vo(user, roleIds(id));
    }

    @Transactional
    @OperationLog(value = "system:user:create", targetType = "user")
    @Override
    public Long saveUser(SysUserCreateDTO dto) {
        lockAndValidateOperator("system:user:create");
        Set<Long> requested = selection(dto.roleIds());
        if (requested.isEmpty()) {
            throw invalid("新增用户至少需要分配一个角色");
        }
        require("system:user:assign-role");
        validateAssignment(requested, Set.of());
        SysUser user =
                values(
                        dto.name(),
                        dto.deptId(),
                        dto.status() == null ? 1 : dto.status(),
                        dto.remark());
        if (dto.avatarUploadId() != null) {
            user.setAvatarUrl(
                    files.resolveImage(dto.avatarUploadId(), permissions.current().user().getId()));
        }
        user.setUsername(dto.username().strip());
        user.setPassword(passwords.encode(PasswordPolicy.TEMPORARY_PASSWORD));
        user.setMustChangePassword(true);
        user.setAuthVersion(0L);
        try {
            if (mapper.insert(user) != 1) {
                throw new IllegalStateException("User was not inserted");
            }
            replaceRoles(user.getId(), requested);
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }
        return user.getId();
    }

    @Transactional
    @OperationLog(value = "system:user:update", targetType = "user", targetIdArgument = 0)
    @Override
    public void updateUser(Long id, SysUserUpdateDTO dto) {
        lockAndValidateOperator("system:user:update");
        SysUser previous = lockTarget(id);
        int status = dto.status() == null ? previous.getStatus() : dto.status();
        boolean changed = previous.getStatus() != status;
        if (changed) {
            protectBuiltin(id);
        }
        Set<Long> existing = roleIds(id);
        checkScope(existing);
        if (previous.getStatus() == 0 && status == 1) {
            require("system:user:assign-role");
        }
        SysUser user = values(dto.name(), dto.deptId(), status, dto.remark());
        user.setId(id);
        try {
            if (mapper.updateProfile(user) != 1) {
                throw notFound();
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }
        if (dto.avatarUploadId() != null) {
            String avatar =
                    files.resolveImage(dto.avatarUploadId(), permissions.current().user().getId());
            if (mapper.updateOwnAvatar(id, avatar) != 1) {
                throw notFound();
            }
        }
        if (changed) {
            invalidateSessions(id);
        } else {
            changes.profileChanged(id);
        }
    }

    @Transactional
    @OperationLog(value = "system:user:assign-role", targetType = "user", targetIdArgument = 0)
    @Override
    public void assignRoles(Long id, SysUserRolesDTO dto) {
        lockAndValidateOperator("system:user:assign-role");
        lockTarget(id);
        protectBuiltin(id);
        Set<Long> before = roleIds(id);
        checkScope(before);
        Set<Long> after = selection(dto.roleIds());
        validateAssignment(after, before);
        if (!before.equals(after)) {
            try {
                replaceRoles(id, after);
            } catch (DataIntegrityViolationException exception) {
                throw conflict();
            }
            invalidateSessions(id);
        }
    }

    @Transactional
    @OperationLog(value = "system:user:reset-password", targetType = "user", targetIdArgument = 0)
    @Override
    public void resetPassword(Long id) {
        lockAndValidateOperator("system:user:reset-password");
        lockTarget(id);
        protectBuiltin(id);
        checkScope(roleIds(id));
        if (mapper.resetPassword(id, passwords.encode(PasswordPolicy.TEMPORARY_PASSWORD)) != 1) {
            throw notFound();
        }
        invalidateSessions(id);
    }

    @Transactional
    @OperationLog(value = "system:user:delete", targetType = "user", targetIdArgument = 0)
    @Override
    public void removeUser(Long id) {
        lockAndValidateOperator("system:user:delete");
        lockTarget(id);
        protectBuiltin(id);
        checkScope(roleIds(id));
        // 同一事务内先更新版本并注册提交后缓存失效，再清理外键关联；日志无用户外键，保留快照。
        invalidateSessions(id);
        mapper.removeSessions(id);
        relations.removeRoles(id);
        try {
            if (mapper.deleteById(id) != 1) {
                throw notFound();
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }
    }

    private void lockAndValidateOperator(String code) {
        // 与角色、菜单、部门管理使用相同互斥行，防止角色范围校验与实际授权写入交错。
        if (roles.lockRoleWrites() == null) {
            throw new IllegalStateException("Active builtin role is required");
        }
        var context = permissions.current();
        SysUser current = mapper.lockUser(context.user().getId());
        if (current == null
                || current.getStatus() != 1
                || Boolean.TRUE.equals(current.getMustChangePassword())
                || !Objects.equals(current.getAuthVersion(), context.user().getAuthVersion())) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "授权已变化，请重新登录");
        }
        require(code);
    }

    private void require(String code) {
        if (!permissions.current().permissions().codes().contains(code)) {
            throw forbidden("当前操作需要对应用户管理权限");
        }
    }

    private SysUser lockTarget(Long id) {
        SysUser user = mapper.lockUser(id);
        if (user == null) {
            throw notFound();
        }
        return user;
    }

    private void protectBuiltin(Long id) {
        if (relations.countBuiltin(id) > 0) {
            throw forbidden("唯一超级管理员账号不允许此操作");
        }
    }

    private void validateAssignment(Set<Long> requested, Set<Long> existing) {
        for (Long id : requested) {
            var role = roles.selectById(id);
            if (role == null) {
                throw invalid("角色不存在");
            }
            if (Boolean.TRUE.equals(role.getBuiltIn())
                    || "super_admin".equals(role.getRoleCode())) {
                throw forbidden("不能分配内置超级管理员角色");
            }
            // 已有停用角色可保留；只阻止新增停用角色，不让幂等重提意外失败。
            if (role.getStatus() != 1 && !existing.contains(id)) {
                throw invalid("只能新增分配启用的角色");
            }
        }
        checkScope(requested);
    }

    private void checkScope(Set<Long> requested) {
        if (requested.isEmpty() || permissions.current().roles().contains("super_admin")) {
            return;
        }
        Set<String> names = new HashSet<>();
        collectNames(permissions.current().permissions().routes(), names);
        Set<Long> direct = permissionMapper.listGrantedIds(permissions.current().user().getId());
        Set<Long> allowed =
                permissionMapper.listMenus().stream()
                        .filter(
                                menu ->
                                        names.contains(menu.getName())
                                                || "button".equals(menu.getType())
                                                        && direct.contains(menu.getId())
                                                        && permissions
                                                                .current()
                                                                .permissions()
                                                                .codes()
                                                                .contains(menu.getAuthCode()))
                        .map(com.maplehush.admin.system.menu.entity.SysMenu::getId)
                        .collect(Collectors.toSet());
        Set<Long> proposed =
                grants.listGrants(requested.stream().sorted().toList()).stream()
                        .map(SysRoleMenuMapper.Grant::menuId)
                        .collect(Collectors.toSet());
        if (!allowed.containsAll(proposed)) {
            throw forbidden("目标用户或拟分配角色超出本人有效授权范围");
        }
    }

    private void collectNames(List<MenuRouteVO> routes, Set<String> names) {
        for (var route : routes) {
            names.add(route.name());
            if (route.children() != null) {
                collectNames(route.children(), names);
            }
        }
    }

    private void invalidateSessions(Long id) {
        changes.usersChanged(List.of(id));
        mapper.revokeSessions(id);
    }

    private SysUser values(String name, String department, Integer status, String remark) {
        Long dept =
                department == null || department.isBlank() || "0".equals(department)
                        ? null
                        : positiveId(department);
        if (dept != null && departments.lockDept(dept) == null) {
            throw invalid("所属部门不存在");
        }
        SysUser user = new SysUser();
        user.setRealName(name.strip());
        user.setDeptId(dept);
        user.setStatus(status);
        user.setRemark(remark == null || remark.isBlank() ? null : remark);
        return user;
    }

    private Set<Long> selection(List<String> ids) {
        if (ids == null) {
            return Set.of();
        }
        if (ids.size() > 100) {
            throw invalid("角色数量超过限制");
        }
        return ids.stream().map(this::positiveId).collect(Collectors.toSet());
    }

    private Long positiveId(String value) {
        try {
            if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
                throw new IllegalArgumentException();
            }
            return Long.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalid("ID 须为正整数字符串且不超过 Long 范围");
        }
    }

    private Set<Long> roleIds(Long id) {
        return memberships(List.of(id)).getOrDefault(id, Set.of());
    }

    private Map<Long, Set<Long>> memberships(List<Long> ids) {
        Map<Long, Set<Long>> result = new HashMap<>();
        if (!ids.isEmpty()) {
            for (var row : relations.listRelations(ids)) {
                result.computeIfAbsent(row.userId(), ignored -> new HashSet<>()).add(row.roleId());
            }
        }
        return result;
    }

    private void replaceRoles(Long user, Set<Long> selected) {
        relations.removeRoles(user);
        if (!selected.isEmpty()
                && relations.saveRoles(user, selected.stream().sorted().toList())
                        != selected.size()) {
            throw new IllegalStateException("User roles were not fully inserted");
        }
    }

    private SysUserVO vo(SysUser user, Set<Long> roles) {
        return new SysUserVO(
                user.getId().toString(),
                user.getUsername(),
                user.getAvatarUrl(),
                user.getRealName(),
                user.getDeptId() == null ? null : user.getDeptId().toString(),
                roles.stream().sorted().map(String::valueOf).toList(),
                relations.countBuiltin(user.getId()) > 0,
                user.getStatus(),
                user.getRemark(),
                user.getCreateTime(),
                user.getUpdateTime());
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
    }

    private BusinessException conflict() {
        return new BusinessException(ErrorCode.CONFLICT, "登录账号已存在或有关联状态冲突");
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
    }

    private BusinessException forbidden(String message) {
        return new BusinessException(ErrorCode.FORBIDDEN, message);
    }
}
