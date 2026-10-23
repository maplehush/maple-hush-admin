package com.maplehush.admin.system.role.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.maplehush.admin.common.annotation.OperationLog;
import com.maplehush.admin.common.api.PageResult;
import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.system.menu.entity.SysMenu;
import com.maplehush.admin.system.menu.vo.MenuRouteVO;
import com.maplehush.admin.system.permission.mapper.PermissionMapper;
import com.maplehush.admin.system.permission.service.IAuthorizationChangeService;
import com.maplehush.admin.system.permission.service.IPermissionService;
import com.maplehush.admin.system.role.dto.SysRoleQueryDTO;
import com.maplehush.admin.system.role.dto.SysRoleSaveDTO;
import com.maplehush.admin.system.role.entity.SysRole;
import com.maplehush.admin.system.role.mapper.SysRoleMapper;
import com.maplehush.admin.system.role.mapper.SysRoleMenuMapper;
import com.maplehush.admin.system.role.service.ISysRoleService;
import com.maplehush.admin.system.role.support.SysRoleGrantRules;
import com.maplehush.admin.system.role.vo.SysRoleVO;

import org.springframework.dao.DataIntegrityViolationException;
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
 * 角色属性与菜单授权的事务边界；编码稳定，内置角色不进入普通写流程
 */
@Service
public class SysRoleServiceImpl implements ISysRoleService {
    private final SysRoleMapper mapper;
    private final SysRoleMenuMapper relations;
    private final PermissionMapper permissionMapper;
    private final IPermissionService permissions;
    private final IAuthorizationChangeService changes;

    public SysRoleServiceImpl(
            SysRoleMapper mapper,
            SysRoleMenuMapper relations,
            PermissionMapper permissionMapper,
            IPermissionService permissions,
            IAuthorizationChangeService changes) {
        this.mapper = mapper;
        this.relations = relations;
        this.permissionMapper = permissionMapper;
        this.permissions = permissions;
        this.changes = changes;
    }

    @Transactional(readOnly = true)
    @Override
    public PageResult<SysRoleVO> listRoles(SysRoleQueryDTO query) {
        var wrapper =
                Wrappers.<SysRole>lambdaQuery()
                        .like(query.name() != null, SysRole::getRoleName, query.name())
                        .eq(query.status() != null, SysRole::getStatus, query.status())
                        .ge(query.startTime() != null, SysRole::getCreateTime, query.startTime())
                        .le(query.endTime() != null, SysRole::getCreateTime, query.endTime())
                        .orderByAsc(SysRole::getId);
        var page = mapper.selectPage(new Page<SysRole>(query.page(), query.pageSize()), wrapper);
        var grants = grants(page.getRecords().stream().map(SysRole::getId).toList());
        List<SysMenu> menus = permissionMapper.listMenus();
        return new PageResult<>(
                page.getRecords().stream()
                        .map(
                                role ->
                                        vo(
                                                role,
                                                grants.getOrDefault(role.getId(), Set.of()),
                                                menus))
                        .toList(),
                page.getTotal());
    }

    @Transactional(readOnly = true)
    @Override
    public SysRoleVO getRole(Long id) {
        return vo(
                getNode(id),
                grants(List.of(id)).getOrDefault(id, Set.of()),
                permissionMapper.listMenus());
    }

    @Transactional
    @OperationLog(value = "system:role:create", targetType = "role")
    @Override
    public Long saveRole(SysRoleSaveDTO dto) {
        lockAndValidateOperator();
        if (dto.roleCode() == null) {
            throw SysRoleGrantRules.invalid("创建角色须提供稳定编码");
        }
        if ("super_admin".equals(dto.roleCode())) {
            throw forbidden("不能创建内置超级管理员角色");
        }
        List<SysMenu> menus = permissionMapper.listMenus();
        Set<Long> selected = withoutDefaultWorkspace(selection(dto.permissions()), menus);
        if (!selected.isEmpty()) {
            requireGrant();
        }
        Set<Long> normalized = SysRoleGrantRules.normalize(selected, Set.of(), menus);
        if (!normalized.isEmpty()) {
            checkGrantScope(normalized, menus);
        }
        SysRole role = values(dto);
        role.setRoleCode(dto.roleCode());
        role.setBuiltIn(false);
        try {
            if (mapper.insert(role) != 1) {
                throw new IllegalStateException("Role insert did not affect one row");
            }
            replaceGrants(role.getId(), normalized);
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }
        return role.getId();
    }

    @Transactional
    @OperationLog(value = "system:role:update", targetType = "role", targetIdArgument = 0)
    @Override
    public void updateRole(Long id, SysRoleSaveDTO dto) {
        lockAndValidateOperator();
        SysRole previous = getNode(id);
        protectBuiltin(previous);
        if (dto.roleCode() != null && !dto.roleCode().equals(previous.getRoleCode())) {
            throw new BusinessException(ErrorCode.CONFLICT, "角色编码创建后不可修改");
        }
        List<SysMenu> menus = permissionMapper.listMenus();
        Set<Long> before =
                withoutDefaultWorkspace(grants(List.of(id)).getOrDefault(id, Set.of()), menus);
        Set<Long> after =
                dto.permissions() == null
                        ? before
                        : SysRoleGrantRules.normalize(
                                withoutDefaultWorkspace(selection(dto.permissions()), menus),
                                before,
                                menus);
        SysRole candidate = values(dto);
        candidate.setId(id);
        if (dto.status() == null) {
            candidate.setStatus(previous.getStatus());
        }
        boolean changed = !before.equals(after);
        boolean enabling = previous.getStatus() == 0 && candidate.getStatus() == 1;
        if (changed || enabling) {
            requireGrant();
            checkGrantScope(after, menus);
        }
        try {
            if (mapper.updateRole(candidate) != 1) {
                throw notFound();
            }
            if (changed) {
                replaceGrants(id, after);
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }
        if (changed || !Objects.equals(previous.getStatus(), candidate.getStatus())) {
            changes.roleChanged(id);
        }
    }

    @Transactional
    @OperationLog(value = "system:role:update", targetType = "role", targetIdArgument = 0)
    @Override
    public void updateRoleStatus(Long id, Integer status) {
        lockAndValidateOperator();
        SysRole role = getNode(id);
        protectBuiltin(role);
        if (Objects.equals(role.getStatus(), status)) {
            return;
        }
        if (Integer.valueOf(1).equals(status)) {
            requireGrant();
            List<SysMenu> menus = permissionMapper.listMenus();
            Set<Long> current = grants(List.of(id)).getOrDefault(id, Set.of());
            checkGrantScope(withoutDefaultWorkspace(current, menus), menus);
        }
        if (mapper.updateRoleStatus(id, status) != 1) {
            throw notFound();
        }
        changes.roleChanged(id);
    }

    @Transactional
    @OperationLog(value = "system:role:delete", targetType = "role", targetIdArgument = 0)
    @Override
    public void removeRole(Long id) {
        lockAndValidateOperator();
        protectBuiltin(getNode(id));
        // 必须在删除用户关联前捕获原持有者并递增版本；回滚时版本与清缓存回调一并撤销。
        changes.roleChanged(id);
        relations.removeUsers(id);
        relations.removeGrants(id);
        try {
            if (mapper.deleteById(id) != 1) {
                throw notFound();
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }
    }

    private void lockAndValidateOperator() {
        if (mapper.lockRoleWrites() == null) {
            throw new IllegalStateException("Active builtin role is required");
        }
        var context = permissions.current();
        var user = permissionMapper.getUser(context.user().getId());
        if (user == null
                || !Integer.valueOf(1).equals(user.getStatus())
                || Boolean.TRUE.equals(user.getMustChangePassword())
                || !Objects.equals(user.getAuthVersion(), context.user().getAuthVersion())) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "授权已变化，请重新登录");
        }
    }

    private void checkGrantScope(Set<Long> requested, List<SysMenu> menus) {
        var context = permissions.current();
        if (context.roles().contains("super_admin")) {
            return;
        }
        Set<String> visibleNames = new HashSet<>();
        collectNames(context.permissions().routes(), visibleNames);
        Set<Long> directlyGranted = permissionMapper.listGrantedIds(context.user().getId());
        Set<Long> allowed =
                menus.stream()
                        .filter(
                                menu ->
                                        visibleNames.contains(menu.getName())
                                                || "button".equals(menu.getType())
                                                        && directlyGranted.contains(menu.getId())
                                                        && context.permissions()
                                                                .codes()
                                                                .contains(menu.getAuthCode()))
                        .map(SysMenu::getId)
                        .collect(Collectors.toSet());
        if (!allowed.containsAll(requested)) {
            throw forbidden("只能授予本人现有有效菜单权限");
        }
    }

    private void requireGrant() {
        if (!permissions.current().permissions().codes().contains("system:role:grant")) {
            throw forbidden("菜单授权或启用角色还需要角色授权权限");
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

    private Set<Long> selection(List<String> ids) {
        return ids == null
                ? Set.of()
                : ids.stream().map(SysRoleGrantRules::id).collect(Collectors.toSet());
    }

    /** Removes universal workspace nodes from role grants and authorization scope. */
    private Set<Long> withoutDefaultWorkspace(Set<Long> requested, List<SysMenu> menus) {
        Set<Long> result = new HashSet<>(requested);
        for (SysMenu menu : menus) {
            if ("Dashboard".equals(menu.getName()) || "Workspace".equals(menu.getName())) {
                result.remove(menu.getId());
            }
        }
        return result;
    }

    private Map<Long, Set<Long>> grants(List<Long> roles) {
        Map<Long, Set<Long>> result = new HashMap<>();
        if (!roles.isEmpty()) {
            for (var row : relations.listGrants(roles)) {
                result.computeIfAbsent(row.roleId(), ignored -> new HashSet<>()).add(row.menuId());
            }
        }
        return result;
    }

    private void replaceGrants(Long role, Set<Long> menus) {
        relations.removeGrants(role);
        if (!menus.isEmpty()
                && relations.saveGrants(role, menus.stream().sorted().toList()) != menus.size()) {
            throw new IllegalStateException("Role grants were not fully inserted");
        }
    }

    private SysRole values(SysRoleSaveDTO dto) {
        SysRole role = new SysRole();
        role.setRoleName(dto.name().strip());
        role.setStatus(dto.status() == null ? 1 : dto.status());
        role.setRemark(dto.remark() == null || dto.remark().isBlank() ? null : dto.remark());
        return role;
    }

    private SysRole getNode(Long id) {
        SysRole role = mapper.selectById(id);
        if (role == null) {
            throw notFound();
        }
        return role;
    }

    private void protectBuiltin(SysRole role) {
        if (Boolean.TRUE.equals(role.getBuiltIn()) || "super_admin".equals(role.getRoleCode())) {
            throw forbidden("内置角色不可通过普通接口修改或删除");
        }
    }

    private SysRoleVO vo(SysRole role, Set<Long> ids, List<SysMenu> menus) {
        Set<Long> rolePermissions = withoutDefaultWorkspace(ids, menus);
        return new SysRoleVO(
                role.getId().toString(),
                role.getRoleCode(),
                role.getRoleName(),
                role.getStatus(),
                role.getBuiltIn(),
                role.getRemark(),
                rolePermissions.stream().sorted().map(String::valueOf).toList(),
                role.getCreateTime(),
                role.getUpdateTime());
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
    }

    private BusinessException forbidden(String message) {
        return new BusinessException(ErrorCode.FORBIDDEN, message);
    }

    private BusinessException conflict() {
        return new BusinessException(ErrorCode.CONFLICT, "角色编码已存在或存在关联冲突");
    }
}
