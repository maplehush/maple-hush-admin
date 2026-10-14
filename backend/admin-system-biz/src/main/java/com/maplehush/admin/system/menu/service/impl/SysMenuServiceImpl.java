package com.maplehush.admin.system.menu.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.maplehush.admin.common.annotation.OperationLog;
import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.system.menu.dto.SysMenuSaveDTO;
import com.maplehush.admin.system.menu.entity.SysMenu;
import com.maplehush.admin.system.menu.mapper.SysMenuMapper;
import com.maplehush.admin.system.menu.service.ISysMenuService;
import com.maplehush.admin.system.menu.support.SysMenuInput;
import com.maplehush.admin.system.menu.support.SysMenuRules;
import com.maplehush.admin.system.menu.vo.MenuRouteVO;
import com.maplehush.admin.system.menu.vo.SysMenuVO;
import com.maplehush.admin.system.permission.mapper.PermissionMapper;
import com.maplehush.admin.system.permission.service.IAuthorizationChangeService;
import com.maplehush.admin.system.permission.service.IPermissionService;
import com.maplehush.admin.system.permission.support.PermissionGraph;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 菜单管理、完整路径校验、内置角色授权与版本失效的业务事务
 */
@Service
public class SysMenuServiceImpl implements ISysMenuService {
    private final SysMenuMapper mapper;
    private final PermissionMapper users;
    private final SysMenuInput input;
    private final PermissionGraph graph;
    private final IPermissionService permissions;
    private final IAuthorizationChangeService changes;

    public SysMenuServiceImpl(
            SysMenuMapper mapper,
            PermissionMapper users,
            SysMenuInput input,
            PermissionGraph graph,
            IPermissionService permissions,
            IAuthorizationChangeService changes) {
        this.mapper = mapper;
        this.users = users;
        this.input = input;
        this.graph = graph;
        this.permissions = permissions;
        this.changes = changes;
    }

    @Transactional(readOnly = true)
    @Override
    public List<SysMenuVO> listMenus() {
        var menus = listNodes();
        return tree(null, menus, SysMenuRules.fullPaths(menus));
    }

    @Override
    public boolean nameExists(String name, Long excluded) {
        if (name == null || !name.matches("[A-Za-z][A-Za-z0-9_-]{0,99}")) {
            throw SysMenuRules.invalid("菜单名称无效");
        }
        return mapper.selectCount(
                        Wrappers.<SysMenu>lambdaQuery()
                                .eq(SysMenu::getName, name)
                                .ne(excluded != null, SysMenu::getId, excluded))
                > 0;
    }

    @Transactional(readOnly = true)
    @Override
    public boolean pathExists(String path, Long excluded) {
        String canonical = SysMenuRules.path(path);
        return SysMenuRules.fullPaths(listNodes()).entrySet().stream()
                .anyMatch(
                        entry ->
                                !Objects.equals(excluded, entry.getKey())
                                        && canonical.equalsIgnoreCase(entry.getValue()));
    }

    @Transactional
    @OperationLog(value = "system:menu:create", targetType = "menu")
    @Override
    public Long saveMenu(SysMenuSaveDTO dto) {
        Long builtin = lockAndValidateOperator();
        var menus = new ArrayList<>(listNodes());
        SysMenu candidate = prepare(dto, -1L, menus);
        checkScope(candidate, menus, false);
        menus.add(candidate);
        SysMenuRules.fullPaths(menus);
        candidate.setId(null);
        try {
            if (mapper.insert(candidate) != 1) {
                throw new IllegalStateException("Menu insert did not affect one row");
            }
            mapper.saveBuiltinGrant(builtin, candidate.getId());
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }
        changes.menusChanged();
        return candidate.getId();
    }

    @Transactional
    @OperationLog(value = "system:menu:update", targetType = "menu", targetIdArgument = 0)
    @Override
    public void updateMenu(Long id, SysMenuSaveDTO dto) {
        lockAndValidateOperator();
        var menus = new ArrayList<>(listNodes());
        SysMenu original = getNode(id, menus);
        checkSubtreeScope(id, menus);
        SysMenu candidate = prepare(dto, id, menus);
        checkScope(candidate, menus, false);
        menus.remove(original);
        menus.add(candidate);
        SysMenuRules.fullPaths(menus);
        try {
            if (mapper.updateMenu(candidate) != 1) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "菜单不存在");
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }
        changes.menusChanged();
    }

    @Transactional
    @OperationLog(value = "system:menu:delete", targetType = "menu", targetIdArgument = 0)
    @Override
    public void removeMenu(Long id) {
        lockAndValidateOperator();
        var menus = listNodes();
        getNode(id, menus);
        checkSubtreeScope(id, menus);
        if (menus.stream().anyMatch(menu -> id.equals(menu.getParentId()))) {
            throw new BusinessException(ErrorCode.CONFLICT, "菜单仍有下级，须先处理下级节点");
        }
        Set<String> removedPath = new HashSet<>();
        String full = SysMenuRules.fullPaths(menus).get(id);
        if (full != null) {
            removedPath.add(full);
        }
        if (menus.stream()
                .anyMatch(
                        menu ->
                                menu.getRedirect() != null
                                        && removedPath.contains(menu.getRedirect()))) {
            throw new BusinessException(ErrorCode.CONFLICT, "菜单仍被重定向引用");
        }
        changes.menusChanged();
        mapper.removeGrants(id);
        if (mapper.deleteById(id) != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "菜单不存在");
        }
    }

    private Long lockAndValidateOperator() {
        Long builtin = mapper.lockMenuWrites();
        if (builtin == null) {
            throw new IllegalStateException("Active builtin role is required");
        }
        var context = permissions.current();
        var user = users.getUser(context.user().getId());
        // 等待菜单写锁期间发生授权变更时，不能沿用进入请求时的旧权限。
        if (user == null
                || !Integer.valueOf(1).equals(user.getStatus())
                || Boolean.TRUE.equals(user.getMustChangePassword())
                || !Objects.equals(user.getAuthVersion(), context.user().getAuthVersion())) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "授权已变化，请重新登录");
        }
        return builtin;
    }

    private SysMenu prepare(SysMenuSaveDTO dto, Long id, List<SysMenu> menus) {
        SysMenu candidate = input.convert(dto);
        candidate.setId(id);
        if (candidate.getParentId() != null && candidate.getParentId().equals(id)) {
            throw SysMenuRules.invalid("菜单不能是自己的父级");
        }
        if (candidate.getPath() != null) {
            String parentPath =
                    candidate.getParentId() == null
                            ? null
                            : SysMenuRules.fullPaths(menus).get(candidate.getParentId());
            if (candidate.getParentId() != null && parentPath == null) {
                throw SysMenuRules.invalid("父目录不存在或不是路由节点");
            }
            candidate.setPath(SysMenuRules.relativePath(candidate.getPath(), parentPath));
        }
        return candidate;
    }

    private void checkSubtreeScope(Long id, List<SysMenu> menus) {
        checkScope(getNode(id, menus), menus, true);
        for (SysMenu node : menus) {
            if (id.equals(node.getParentId())) {
                checkSubtreeScope(node.getId(), menus);
            }
        }
    }

    private void checkScope(SysMenu node, List<SysMenu> menus, boolean existing) {
        var context = permissions.current();
        if (context.roles().contains("super_admin")) {
            return;
        }
        Set<String> visible = new HashSet<>();
        collectNames(context.permissions().routes(), visible);
        if (node.getAuthCode() != null
                && !context.permissions().codes().contains(node.getAuthCode())) {
            throw forbidden();
        }
        // 现有可见性按修改前身份验证；候选节点只复核新操作码与父节点，允许正常重命名。
        if (existing
                && !"catalog".equals(node.getType())
                && node.getAuthCode() == null
                && !visible.contains(node.getName())) {
            throw forbidden();
        }
        if (existing && "catalog".equals(node.getType()) && !visible.contains(node.getName())) {
            throw forbidden();
        }
        if (node.getParentId() != null) {
            SysMenu parent = getNode(node.getParentId(), menus);
            if (!visible.contains(parent.getName())) {
                throw forbidden();
            }
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

    private List<SysMenu> listNodes() {
        return mapper.selectList(
                Wrappers.<SysMenu>lambdaQuery()
                        .orderByAsc(SysMenu::getSort)
                        .orderByAsc(SysMenu::getId));
    }

    private SysMenu getNode(Long id, List<SysMenu> menus) {
        return menus.stream()
                .filter(menu -> menu.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "菜单不存在"));
    }

    private List<SysMenuVO> tree(Long parent, List<SysMenu> menus, Map<Long, String> paths) {
        return menus.stream()
                .filter(menu -> Objects.equals(parent, menu.getParentId()))
                .map(
                        menu -> {
                            List<SysMenuVO> children = tree(menu.getId(), menus, paths);
                            return new SysMenuVO(
                                    menu.getId().toString(),
                                    parent == null ? null : parent.toString(),
                                    menu.getName(),
                                    menu.getType(),
                                    menu.getStatus(),
                                    paths.get(menu.getId()),
                                    menu.getComponent(),
                                    menu.getRedirect(),
                                    menu.getAuthCode(),
                                    graph.metadata(menu),
                                    children.isEmpty() ? null : children);
                        })
                .toList();
    }

    private BusinessException conflict() {
        return new BusinessException(ErrorCode.CONFLICT, "菜单名称、路径或权限码已存在，或存在关联冲突");
    }

    private BusinessException forbidden() {
        return new BusinessException(ErrorCode.FORBIDDEN, "不能管理超出本人有效授权范围的菜单");
    }
}
