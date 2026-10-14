package com.maplehush.admin.system.role.support;

import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.system.menu.entity.SysMenu;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 按节点 ID 规范化角色授权；祖先目录仅为导航，不添加其他页面或按钮。 */
public final class SysRoleGrantRules {
    private SysRoleGrantRules() {}

    public static Long id(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw invalid("角色或菜单 ID 无效");
        try { return Long.valueOf(value); }
        catch (NumberFormatException exception) { throw invalid("ID 超出范围"); }
    }

    /** 保留旧按钮但取消其旧页面时，按页面移除语义清理按钮；新孤立按钮仍拒绝。 */
    public static Set<Long> normalize(Set<Long> requested, Set<Long> previous, List<SysMenu> menus) {
        Map<Long, SysMenu> indexed = new HashMap<>(); menus.forEach(menu -> indexed.put(menu.getId(), menu));
        Set<Long> result = new HashSet<>(requested);
        for (Long id : requested) {
            SysMenu menu = indexed.get(id);
            if (menu == null) throw invalid("授权菜单不存在");
            if ("button".equals(menu.getType()) && !requested.contains(menu.getParentId())) {
                if (previous.contains(id) && previous.contains(menu.getParentId())) result.remove(id);
                else throw invalid("按钮必须与所属页面同时授权");
            }
        }
        for (Long id : Set.copyOf(result)) {
            SysMenu current = indexed.get(id); Set<Long> visited = new HashSet<>();
            int routeDepth = 0;
            while (current != null) {
                if (!visited.add(current.getId()) || !"button".equals(current.getType()) && ++routeDepth > 32) {
                    throw invalid("授权菜单层级存在循环或过深");
                }
                if (current.getParentId() == null) {
                    if ("button".equals(current.getType())) throw invalid("按钮必须归属页面");
                    break;
                }
                SysMenu parent = indexed.get(current.getParentId());
                if (parent == null) throw invalid("授权菜单父节点不存在");
                if ("button".equals(current.getType())) {
                    if (!Set.of("menu", "embedded", "link").contains(parent.getType())) throw invalid("按钮必须归属页面");
                } else if (!"catalog".equals(parent.getType())) throw invalid("页面祖先必须为目录");
                result.add(parent.getId()); current = parent;
            }
        }
        return Set.copyOf(result);
    }

    public static BusinessException invalid(String message) { return new BusinessException(ErrorCode.INVALID_ARGUMENT, message); }
}
