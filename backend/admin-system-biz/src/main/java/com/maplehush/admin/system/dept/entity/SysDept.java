package com.maplehush.admin.system.dept.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.maplehush.admin.common.entity.BaseEntity;

import lombok.Getter;
import lombok.Setter;

/**
 * 系统部门；树形循环由业务层校验
 */
@Getter
@Setter
@TableName("sys_dept")
public class SysDept extends BaseEntity {
    /**
     * 数据库主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 父节点 ID；根节点为空
     */
    private Long parentId;

    /**
     * 部门显示名称
     */
    private String name;

    /**
     * 排序值；越小越靠前，相同值按 ID 升序
     */
    private Integer sort;

    /**
     * 启用状态：1 启用，0 停用
     */
    private Integer status;

    /**
     * 业务备注
     */
    private String remark;
}
