package com.maplehush.admin.system.permission.support;

import com.maplehush.admin.system.menu.entity.SysMenu;
import com.maplehush.admin.system.menu.vo.MenuRouteVO;

import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 统一计算有效授权和路由，断链、循环、停用祖先均拒绝生效
 */
@Component
public class PermissionGraph {
    private static final Set<String> PAGES = Set.of("menu", "embedded", "link");
    private static final Set<String> BOOLEANS =
            Set.of(
                    "affixTab",
                    "breadcrumbUseActivePath",
                    "domCached",
                    "fullPathKey",
                    "hideChildrenInMenu",
                    "hideInBreadcrumb",
                    "hideInMenu",
                    "hideInTab",
                    "keepAlive",
                    "noBasicLayout",
                    "openInNewWindow");
    private static final Set<String> STRINGS =
            Set.of("activeIcon", "activePath", "badge", "badgeType", "badgeVariants");
    private final JsonMapper json;

    public PermissionGraph(JsonMapper json) {
        this.json = json;
    }

    /**
     * 结合启用状态、目录祖先和页面/按钮关系计算有效授权
     *
     * @param menus 当前数据库菜单节点
     * @param granted 活跃角色直接获授的菜单节点 ID
     * @return 有效权限码与可访问的动态路由
     */
    public Resolved resolve(List<SysMenu> menus, Set<Long> granted) {
        Map<Long, SysMenu> nodes = new HashMap<>();
        menus.forEach(menu -> nodes.put(menu.getId(), menu));
        Set<Long> visible = new HashSet<>();
        Set<String> codes = new java.util.TreeSet<>();
        for (SysMenu menu : menus) {
            if (!granted.contains(menu.getId()) || !activeChain(menu, nodes)) {
                continue;
            }
            if (PAGES.contains(menu.getType())) {
                addCode(menu, codes);
                SysMenu current = menu;
                while (current != null) {
                    visible.add(current.getId());
                    current = nodes.get(current.getParentId());
                }
            } else if ("button".equals(menu.getType())) {
                SysMenu page = nodes.get(menu.getParentId());
                if (page != null
                        && PAGES.contains(page.getType())
                        && granted.contains(page.getId())) {
                    addCode(menu, codes);
                }
            }
        }
        Set<String> paths = new HashSet<>();
        visible.forEach(id -> paths.add(fullPath(nodes.get(id), nodes)));
        return new Resolved(Set.copyOf(codes), routes(null, menus, visible, paths));
    }

    private boolean activeChain(SysMenu menu, Map<Long, SysMenu> nodes) {
        Set<Long> visited = new HashSet<>();
        SysMenu current = menu;
        while (current != null) {
            if (!visited.add(current.getId()) || !Integer.valueOf(1).equals(current.getStatus())) {
                return false;
            }
            Long parent = current.getParentId();
            if (parent == null) {
                return !"button".equals(current.getType());
            }
            current = nodes.get(parent);
            // 只有目录可以自动补齐；页面祖先需显式获授，首版不允许嵌套页面。
            if (current == null) {
                return false;
            }
            if (!"catalog".equals(current.getType())
                    && !("button".equals(menu.getType())
                            && parent.equals(menu.getParentId())
                            && PAGES.contains(current.getType()))) {
                return false;
            }
        }
        return false;
    }

    private void addCode(SysMenu menu, Set<String> codes) {
        String code = menu.getAuthCode();
        if (code != null && code.matches("[a-z][a-z0-9-]*:[a-z][a-z0-9-]*:[a-z][a-z0-9-]*")) {
            codes.add(code);
        }
    }

    private String fullPath(SysMenu menu, Map<Long, SysMenu> nodes) {
        if (menu.getParentId() == null) {
            return menu.getPath();
        }
        String parent = fullPath(nodes.get(menu.getParentId()), nodes);
        return parent.replaceAll("/$", "") + "/" + menu.getPath();
    }

    private List<MenuRouteVO> routes(
            Long parent, List<SysMenu> menus, Set<Long> visible, Set<String> paths) {
        List<MenuRouteVO> result = new ArrayList<>();
        menus.stream()
                .filter(
                        menu ->
                                visible.contains(menu.getId())
                                        && java.util.Objects.equals(parent, menu.getParentId()))
                .sorted(Comparator.comparing(SysMenu::getSort).thenComparing(SysMenu::getId))
                .forEach(
                        menu -> {
                            List<MenuRouteVO> children =
                                    routes(menu.getId(), menus, visible, paths);
                            String redirect =
                                    paths.contains(menu.getRedirect()) ? menu.getRedirect() : null;
                            String component = menu.getComponent();
                            if (component == null
                                    && parent == null
                                    && "catalog".equals(menu.getType())) {
                                component = "BasicLayout";
                            }
                            result.add(
                                    new MenuRouteVO(
                                            menu.getName(),
                                            menu.getPath(),
                                            component,
                                            redirect,
                                            metadata(menu),
                                            children.isEmpty() ? null : List.copyOf(children)));
                        });
        return List.copyOf(result);
    }

    /**
     * 从独立列与安全扩展字段组装两类菜单共用的显示元数据
     */
    public Map<String, Object> metadata(SysMenu menu) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (menu.getMetaJson() != null) {
            try {
                var node = json.readTree(menu.getMetaJson());
                for (String key : BOOLEANS) {
                    if (node.has(key) && node.get(key).isBoolean()) {
                        meta.put(key, node.get(key).asBoolean());
                    }
                }
                for (String key : STRINGS) {
                    if (node.has(key) && node.get(key).isString()) {
                        meta.put(key, node.get(key).asString());
                    }
                }
                for (String key : Set.of("affixTabOrder", "maxNumOfOpenTab")) {
                    if (node.has(key) && node.get(key).isIntegralNumber()) {
                        meta.put(key, node.get(key).asInt());
                    }
                }
                for (String key : Set.of("iframeSrc", "link")) {
                    if (node.has(key)
                            && node.get(key).isString()
                            && safeUrl(node.get(key).asString())) {
                        meta.put(key, node.get(key).asString());
                    }
                }
            } catch (RuntimeException ignored) {
                // 无效扩展元数据不影响授权，也不将原始内容交给前端。
            }
        }
        meta.put("title", menu.getTitle());
        meta.put("order", menu.getSort());
        if (menu.getIcon() != null && !menu.getIcon().isBlank()) {
            meta.put("icon", menu.getIcon());
        }
        return Map.copyOf(meta);
    }

    private boolean safeUrl(String value) {
        try {
            URI uri = URI.create(value);
            return Set.of("http", "https").contains(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getUserInfo() == null;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public record Resolved(Set<String> codes, List<MenuRouteVO> routes) {}
}
