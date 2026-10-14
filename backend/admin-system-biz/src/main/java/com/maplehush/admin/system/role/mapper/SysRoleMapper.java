package com.maplehush.admin.system.role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.maplehush.admin.system.role.entity.SysRole;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 角色表基础映射
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {
    /**
     * 与菜单/部门写锁顺序一致，保证菜单集合与角色关联并发校验有同一边界
     */
    @Select(
            "SELECT id FROM sys_role WHERE role_code='super_admin' AND is_builtin=1 AND status=1 FOR UPDATE")
    Long lockRoleWrites();

    @Update(
            "UPDATE sys_role SET role_name=#{roleName},status=#{status},remark=#{remark},update_time=CURRENT_TIMESTAMP WHERE id=#{id}")
    int updateRole(SysRole role);
}
