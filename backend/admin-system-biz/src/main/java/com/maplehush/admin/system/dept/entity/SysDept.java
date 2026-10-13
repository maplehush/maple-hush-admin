package com.maplehush.admin.system.dept.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.maplehush.admin.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 系统部门；树形循环由业务层校验。 */
@Getter
@Setter
@TableName("sys_dept")
public class SysDept extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String name;
    private Integer sort;
    private Integer status;
    private String remark;
}
