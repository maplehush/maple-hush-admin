package com.maplehush.admin.system.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.maplehush.admin.common.entity.BaseEntity;

import lombok.Getter;
import lombok.Setter;

/**
 * 字典类型
 */
@Getter
@Setter
@TableName("sys_dict_type")
public class SysDictType extends BaseEntity {
    /**
     * 数据库主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 唯一的字典类型编码
     */
    private String dictCode;

    /**
     * 字典类型显示名称
     */
    private String dictName;

    /**
     * 启用状态：1 启用，0 停用
     */
    private Integer status;

    /**
     * 业务备注
     */
    private String remark;
}
