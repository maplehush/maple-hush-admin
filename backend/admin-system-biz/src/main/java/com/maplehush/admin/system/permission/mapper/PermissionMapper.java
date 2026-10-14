package com.maplehush.admin.system.permission.mapper;

import com.maplehush.admin.system.menu.entity.SysMenu;
import com.maplehush.admin.system.user.entity.SysUser;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Set;

/**
 * 授权查询只读取活跃角色；不提供用户名或超级管理员旁路
 */
@Mapper
public interface PermissionMapper {
    @Select(
            "SELECT id,username,real_name,avatar_url,introduction,status,must_change_password,auth_version FROM sys_user WHERE id=#{id}")
    SysUser getUser(@Param("id") long id);

    @Select(
            "SELECT id,parent_id,name,title,type,path,component,redirect,auth_code,icon,meta_json,sort,status FROM sys_menu")
    List<SysMenu> listMenus();

    @Select(
            "SELECT DISTINCT rm.menu_id FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id AND r.status=1 "
                    + "JOIN sys_role_menu rm ON rm.role_id=r.id WHERE ur.user_id=#{id}")
    Set<Long> listGrantedIds(@Param("id") long id);

    @Select(
            "SELECT DISTINCT ur.user_id FROM sys_user_role ur WHERE ur.role_id=#{id} ORDER BY ur.user_id")
    List<Long> listRoleUsers(@Param("id") long id);

    // 菜单祖先的状态/路径变更可能影响后代，因此菜单变更统一使所有已分配角色用户失效。
    @Select("SELECT DISTINCT user_id FROM sys_user_role ORDER BY user_id")
    List<Long> listAssignedUsers();

    @Update(
            "UPDATE sys_user SET auth_version=auth_version+1,update_time=CURRENT_TIMESTAMP WHERE id=#{id}")
    int incrementVersion(@Param("id") long id);
}
