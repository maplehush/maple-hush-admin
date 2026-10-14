package com.maplehush.admin.system.role.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.maplehush.admin.common.entity.BaseEntity;

import lombok.Getter;
import lombok.Setter;

/**
 * 系统角色；内置角色的修改保护由业务层执行
 */
@Getter
@Setter
@TableName("sys_role")
public class SysRole extends BaseEntity {
    /**
     * 数据库主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 稳定且唯一的角色编码；创建后不可修改
     */
    private String roleCode;

    /**
     * 角色显示名称
     */
    private String roleName;

    /**
     * 是否为内置角色；内置超级管理员角色禁止普通管理操作变更
     */
    @TableField("is_builtin")
    private Boolean builtIn;

    /**
     * 启用状态：1 启用，0 停用
     */
    private Integer status;

    /**
     * 业务备注
     */
    private String remark;
}
