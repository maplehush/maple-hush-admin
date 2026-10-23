package com.maplehush.admin.system.permission;

import static org.junit.jupiter.api.Assertions.*;

import com.maplehush.admin.system.menu.entity.SysMenu;
import com.maplehush.admin.system.permission.support.PermissionGraph;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;

class PermissionGraphTest {
    private final PermissionGraph graph = new PermissionGraph(JsonMapper.builder().build());

    @Test
    void ancestorDoesNotGrantAuthorityAndOrphanButtonIsRejected() {
        SysMenu root = menu(1, null, "catalog", "/system", "system:menu:list");
        SysMenu page = menu(10, 1L, "menu", "user", "system:user:list");
        SysMenu button = menu(11, 10L, "button", null, "system:user:create");
        var orphan = graph.resolve(List.of(root, page, button), Set.of(11L));
        assertTrue(orphan.codes().isEmpty());
        assertTrue(orphan.routes().isEmpty());
        var granted = graph.resolve(List.of(root, page, button), Set.of(10L, 11L));
        assertEquals(Set.of("system:user:list", "system:user:create"), granted.codes());
        assertEquals("/system", granted.routes().getFirst().path());
        assertEquals("user", granted.routes().getFirst().children().getFirst().path());
        assertNull(granted.routes().getFirst().children().getFirst().children());
        root.setStatus(0);
        assertTrue(graph.resolve(List.of(root, page, button), Set.of(10L, 11L)).codes().isEmpty());
    }

    @Test
    void metadataUsesTypedWhitelistAndExplicitColumnsWin() {
        SysMenu page = menu(10, null, "menu", "/user", "system:user:list");
        page.setMetaJson(
                "{\"title\":\"spoof\",\"order\":999,\"hideInMenu\":true,\"authority\":[\"super_admin\"],\"token\":\"secret\",\"keepAlive\":\"true\"}");
        var route = graph.resolve(List.of(page), Set.of(10L)).routes().getFirst();
        assertEquals("菜单10", route.meta().get("title"));
        assertEquals(10, route.meta().get("order"));
        assertEquals(true, route.meta().get("hideInMenu"));
        assertFalse(route.meta().containsKey("authority"));
        assertFalse(route.meta().containsKey("token"));
        assertFalse(route.meta().containsKey("keepAlive"));
    }

    @Test
    void workspaceIsAvailableWithoutRoleGrantAndAddsNoPermissionCode() {
        SysMenu dashboard = menu(1, null, "catalog", "/dashboard", null);
        dashboard.setName("Dashboard");
        dashboard.setTitle("概览");
        SysMenu workspace = menu(2, 1L, "menu", "workspace", null);
        workspace.setName("Workspace");
        workspace.setTitle("工作台");
        workspace.setComponent("/dashboard/workspace/index");

        var resolved = graph.resolve(List.of(dashboard, workspace), Set.of());

        assertTrue(resolved.codes().isEmpty());
        assertEquals(1, resolved.routes().size());
        assertEquals("Dashboard", resolved.routes().getFirst().name());
        assertEquals("Workspace", resolved.routes().getFirst().children().getFirst().name());
        assertEquals(
                "/dashboard/workspace/index",
                resolved.routes().getFirst().children().getFirst().component());
    }

    private SysMenu menu(long id, Long parent, String type, String path, String code) {
        SysMenu menu = new SysMenu();
        menu.setId(id);
        menu.setParentId(parent);
        menu.setName("Node" + id);
        menu.setTitle("菜单" + id);
        menu.setType(type);
        menu.setPath(path);
        menu.setAuthCode(code);
        menu.setStatus(1);
        menu.setSort((int) id);
        if ("menu".equals(type)) {
            menu.setComponent("/system/user/list");
        }
        return menu;
    }
}
