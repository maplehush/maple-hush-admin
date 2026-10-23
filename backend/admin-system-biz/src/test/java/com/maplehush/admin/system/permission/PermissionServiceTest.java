package com.maplehush.admin.system.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.maplehush.admin.system.permission.mapper.PermissionMapper;
import com.maplehush.admin.system.permission.service.impl.PermissionServiceImpl;
import com.maplehush.admin.system.permission.support.PermissionGraph;
import com.maplehush.admin.system.user.entity.SysUser;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;

class PermissionServiceTest {
    @Test
    void preservesEmptyRolesAndReadsLatestGrantsOnEveryRequest() {
        var mapper = mock(PermissionMapper.class);
        var user = new SysUser();
        user.setId(7L);
        when(mapper.listMenus()).thenReturn(List.of());
        when(mapper.listRoleGrants(7)).thenReturn(List.of(
                new PermissionMapper.RoleGrant("operator", 2L),
                new PermissionMapper.RoleGrant("operator", 3L),
                new PermissionMapper.RoleGrant("empty", null)))
                .thenReturn(List.of());
        var service = new PermissionServiceImpl(mapper, new PermissionGraph(JsonMapper.builder().build()));
        assertEquals(List.of("operator", "empty"), service.load(user).roles());
        assertEquals(List.of(), service.load(user).roles());
        verify(mapper, org.mockito.Mockito.times(2)).listRoleGrants(7);
    }
}
