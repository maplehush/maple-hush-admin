package com.maplehush.admin.system.role.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.maplehush.admin.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 系统角色；内置角色的修改保护由业务层执行。 */
@Getter
@Setter
@TableName("sys_role")
public class SysRole extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String roleCode;
    private String roleName;
    @TableField("is_builtin")
    private Boolean builtIn;
    private Integer status;
    private String remark;
}
