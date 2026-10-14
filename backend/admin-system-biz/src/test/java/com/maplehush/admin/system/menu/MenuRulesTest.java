package com.maplehush.admin.system.menu;

import static org.junit.jupiter.api.Assertions.*;

import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.system.menu.support.SysMenuRules;
import org.junit.jupiter.api.Test;

class MenuRulesTest {
    @Test
    void fullPathMustBeCanonicalAndChildMustRemainUnderParent() {
        assertEquals("user", SysMenuRules.relativePath("/system/user", "/system"));
        assertEquals("/system", SysMenuRules.relativePath("/system", null));
        for (String value : new String[]{"system/user", "/system//user", "/system/../user", "/system/user/", "/system/user?x=1", "/system/:id"}) {
            assertThrows(BusinessException.class, () -> SysMenuRules.path(value));
        }
        assertThrows(BusinessException.class, () -> SysMenuRules.relativePath("/systematic/user", "/system"));
    }
}
