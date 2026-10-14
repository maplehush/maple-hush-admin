package com.maplehush.admin.system.user.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户角色关联；所有写操作由用户服务在共享结构锁内执行
 */
@Mapper
public interface SysUserRoleMapper {
    /**
     * 批量读取分页用户的角色，避免逐行查询
     * @param ids 非空的用户 ID 列表
     * @return 按用户及角色 ID 排序的关联投影
     */
    @Select(
            "<script>SELECT user_id,role_id FROM sys_user_role WHERE user_id IN "
                    + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> ORDER BY user_id,role_id</script>")
    List<Relation> listRelations(@Param("ids") List<Long> ids);

    /**
     * @param id 用户 ID
     * @return 是否关联受保护的内置角色，不能依赖固定用户名或用户 ID
     */
    @Select(
            "SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=#{id} AND (r.role_code='super_admin' OR r.is_builtin=1)")
    int countBuiltin(@Param("id") long id);

    /**
     * 清除用户全部角色关联；替换时与后续插入处于同一事务
     * @param id 用户 ID
     * @return 实际删除的关联数
     */
    @Delete("DELETE FROM sys_user_role WHERE user_id=#{id}")
    int removeRoles(@Param("id") long id);

    /**
     * 批量插入已校验的角色集合
     * @param userId 用户 ID
     * @param roles 非空、去重且排序后的角色 ID 列表
     * @return 实际插入的关联数
     */
    @Insert(
            "<script>INSERT INTO sys_user_role(user_id,role_id) VALUES "
                    + "<foreach collection='roles' item='role' separator=','>(#{userId},#{role})</foreach></script>")
    int saveRoles(@Param("userId") long userId, @Param("roles") List<Long> roles);

    /**
     * 内部关联投影，不直接返回 HTTP
     * @param userId 用户 ID
     * @param roleId 角色 ID
     */
    record Relation(Long userId, Long roleId) {}
}
