package com.maplehush.admin.system.menu.support;

import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.system.menu.entity.SysMenu;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 静态菜单路径与层级规则，不接收 Vue 动态参数、查询串或路径穿越片段
 */
public final class SysMenuRules {
    public static final Set<String> TYPES = Set.of("catalog", "menu", "embedded", "link", "button");

    private SysMenuRules() {}

    public static Long id(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw invalid("ID 无效");
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw invalid("ID 超出范围");
        }
    }

    public static Long parentId(String value) {
        return value == null || value.isBlank() || "0".equals(value) ? null : id(value);
    }

    public static String path(String value) {
        if (value == null
                || value.length() > 255
                || !value.matches("/(?:[A-Za-z0-9_-]+)(?:/[A-Za-z0-9_-]+)*")) {
            throw invalid("路径须为完整静态地址，不能含尾斜杠、动态参数或查询串");
        }
        return value;
    }

    public static String relativePath(String fullPath, String parentPath) {
        path(fullPath);
        if (parentPath == null) {
            return fullPath;
        }
        if (!fullPath.startsWith(parentPath + "/")) {
            throw invalid("子路径必须位于父目录下");
        }
        return fullPath.substring(parentPath.length() + 1);
    }

    /**
     * 校验所有后代的完整路径，目录移动不能造成跨层级重名或失效层级
     */
    public static Map<Long, String> fullPaths(List<SysMenu> menus) {
        Map<Long, SysMenu> nodes = new HashMap<>();
        menus.forEach(menu -> nodes.put(menu.getId(), menu));
        Map<Long, String> paths = new HashMap<>();
        Set<String> unique = new HashSet<>();
        for (SysMenu menu : menus) {
            validateParent(menu, nodes);
            if ("button".equals(menu.getType())) {
                continue;
            }
            String full = fullPath(menu, nodes, new HashSet<>());
            if (!unique.add(full.toLowerCase(Locale.ROOT))) {
                throw new BusinessException(ErrorCode.CONFLICT, "完整访问路径已存在");
            }
            paths.put(menu.getId(), full);
        }
        for (SysMenu menu : menus) {
            if (menu.getRedirect() == null) {
                continue;
            }
            Long next = menu.getId();
            Set<Long> visited = new HashSet<>();
            while (next != null && nodes.get(next).getRedirect() != null) {
                if (!visited.add(next)) {
                    throw invalid("重定向不能形成循环");
                }
                String target = path(nodes.get(next).getRedirect());
                next =
                        paths.entrySet().stream()
                                .filter(entry -> entry.getValue().equals(target))
                                .map(Map.Entry::getKey)
                                .findFirst()
                                .orElseThrow(() -> invalid("重定向目标必须为现有菜单路径"));
            }
        }
        return paths;
    }

    private static void validateParent(SysMenu menu, Map<Long, SysMenu> nodes) {
        if (menu.getParentId() == null) {
            if ("button".equals(menu.getType())) {
                throw invalid("按钮必须归属页面");
            }
            return;
        }
        SysMenu parent = nodes.get(menu.getParentId());
        if (parent == null) {
            throw invalid("父节点不存在");
        }
        if ("button".equals(menu.getType())) {
            if (!Set.of("menu", "embedded", "link").contains(parent.getType())) {
                throw invalid("按钮必须归属页面");
            }
        } else if (!"catalog".equals(parent.getType())) {
            throw invalid("路由节点只能归属目录");
        }
    }

    private static String fullPath(SysMenu menu, Map<Long, SysMenu> nodes, Set<Long> visited) {
        if (!visited.add(menu.getId()) || visited.size() > 32) {
            throw invalid("菜单存在循环或超过 32 层");
        }
        if (menu.getParentId() == null) {
            return path(menu.getPath());
        }
        String child = menu.getPath();
        if (child == null || child.startsWith("/")) {
            throw invalid("数据库子路径必须为相对地址");
        }
        return path(fullPath(nodes.get(menu.getParentId()), nodes, visited) + "/" + child);
    }

    public static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
    }
}
