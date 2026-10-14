package com.maplehush.admin.system.role;

import static org.junit.jupiter.api.Assertions.*;

import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.system.menu.entity.SysMenu;
import com.maplehush.admin.system.role.support.SysRoleGrantRules;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

class RoleGrantRulesTest {
    private final List<SysMenu> menus =
            List.of(
                    node(1, null, "catalog"),
                    node(10, 1L, "menu"),
                    node(11, 10L, "button"),
                    node(20, 1L, "menu"));

    @Test
    void ancestorsAreAddedAndNewOrphanButtonsAreRejected() {
        assertEquals(
                Set.of(1L, 10L, 11L),
                SysRoleGrantRules.normalize(Set.of(10L, 11L), Set.of(), menus));
        assertThrows(
                BusinessException.class,
                () -> SysRoleGrantRules.normalize(Set.of(11L), Set.of(), menus));
    }

    @Test
    void removingPreviouslyGrantedPageAlsoRemovesItsOldButtons() {
        assertEquals(
                Set.of(1L, 20L),
                SysRoleGrantRules.normalize(Set.of(11L, 20L), Set.of(1L, 10L, 11L), menus));
    }

    @Test
    void maximumMenuRouteDepthAlsoAllowsItsButton() {
        var deep = new java.util.ArrayList<SysMenu>();
        for (long id = 1; id <= 31; id++) {
            deep.add(node(id, id == 1 ? null : id - 1, "catalog"));
        }
        deep.add(node(32, 31L, "menu"));
        deep.add(node(33, 32L, "button"));
        assertEquals(33, SysRoleGrantRules.normalize(Set.of(32L, 33L), Set.of(), deep).size());
    }

    @Test
    void unknownNodesAndCyclicAncestorsCannotBeGranted() {
        assertThrows(
                BusinessException.class,
                () -> SysRoleGrantRules.normalize(Set.of(999L), Set.of(), menus));
        var root = node(1, 1L, "catalog");
        assertThrows(
                BusinessException.class,
                () ->
                        SysRoleGrantRules.normalize(
                                Set.of(10L), Set.of(), List.of(root, node(10, 1L, "menu"))));
    }

    private SysMenu node(long id, Long parent, String type) {
        SysMenu menu = new SysMenu();
        menu.setId(id);
        menu.setParentId(parent);
        menu.setType(type);
        return menu;
    }
}
