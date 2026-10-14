package com.maplehush.admin.system.dept.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.maplehush.admin.system.dept.entity.SysDept;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 部门表基础映射。 */
@Mapper
public interface SysDeptMapper extends BaseMapper<SysDept> {
    /** 与菜单写操作使用相同的首版结构变更互斥行，空部门树也能防止并发成环。 */
    @Select("SELECT id FROM sys_role WHERE role_code='super_admin' AND is_builtin=1 AND status=1 FOR UPDATE")
    Long lockTreeWrites();

    @Select("SELECT id,parent_id,name,sort,status,remark,create_time,update_time FROM sys_dept WHERE id=#{id} FOR UPDATE")
    SysDept lockDept(@Param("id") long id);

    @Select("SELECT COUNT(*) FROM sys_user WHERE dept_id=#{id}")
    long countUsers(@Param("id") long id);

    /** 全量修改显式清空 parent_id/remark，不依赖默认 NOT_NULL 更新策略。 */
    @Update("UPDATE sys_dept SET parent_id=#{parentId},name=#{name},sort=#{sort},status=#{status},remark=#{remark},"
            + "update_time=CURRENT_TIMESTAMP WHERE id=#{id}")
    int updateDept(SysDept dept);
}
