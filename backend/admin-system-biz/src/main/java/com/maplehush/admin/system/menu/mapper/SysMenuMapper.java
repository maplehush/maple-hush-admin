package com.maplehush.admin.system.menu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.maplehush.admin.system.menu.entity.SysMenu;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 菜单与权限节点基础映射
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {
    /**
     * 所有菜单写操作先锁唯一内置角色行，串行验证跨层级完整路径
     */
    @Select(
            "SELECT id FROM sys_role WHERE role_code='super_admin' AND is_builtin=1 AND status=1 FOR UPDATE")
    Long lockMenuWrites();

    @Insert("INSERT INTO sys_role_menu(role_id,menu_id) VALUES (#{roleId},#{menuId})")
    int saveBuiltinGrant(@Param("roleId") long roleId, @Param("menuId") long menuId);

    @Delete("DELETE FROM sys_role_menu WHERE menu_id=#{id}")
    int removeGrants(@Param("id") long id);

    // 全量表单更新须显式写 NULL，避免 MyBatis-Plus 默认 NOT_NULL 策略遗留旧路由/元数据。
    @Update(
            "UPDATE sys_menu SET parent_id=#{parentId},name=#{name},title=#{title},type=#{type},path=#{path},"
                    + "component=#{component},redirect=#{redirect},auth_code=#{authCode},icon=#{icon},meta_json=#{metaJson},"
                    + "sort=#{sort},status=#{status},update_time=CURRENT_TIMESTAMP WHERE id=#{id}")
    int updateMenu(SysMenu menu);
}
