package com.maplehush.admin.system.role.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色菜单与用户角色关联操作，写入前由角色服务持有共享结构锁
 */
@Mapper
public interface SysRoleMenuMapper {
    @Select(
            "<script>SELECT role_id,menu_id FROM sys_role_menu WHERE role_id IN "
                    + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> ORDER BY role_id,menu_id</script>")
    List<Grant> listGrants(@Param("ids") List<Long> ids);

    @Delete("DELETE FROM sys_role_menu WHERE role_id=#{id}")
    int removeGrants(@Param("id") long id);

    @Insert(
            "<script>INSERT INTO sys_role_menu(role_id,menu_id) VALUES "
                    + "<foreach collection='menus' item='menu' separator=','>(#{roleId},#{menu})</foreach></script>")
    int saveGrants(@Param("roleId") long roleId, @Param("menus") List<Long> menus);

    @Delete("DELETE FROM sys_user_role WHERE role_id=#{id}")
    int removeUsers(@Param("id") long id);

    /**
     * SQL 投影，不向 HTTP 或其他模块暴露关联表实体
     */
    record Grant(Long roleId, Long menuId) {}
}
