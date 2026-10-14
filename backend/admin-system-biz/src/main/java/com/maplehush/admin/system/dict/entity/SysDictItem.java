package com.maplehush.admin.system.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.maplehush.admin.common.entity.BaseEntity;

import lombok.Getter;
import lombok.Setter;

/**
 * 字典项；同类型单一默认项由业务事务维护
 */
@Getter
@Setter
@TableName("sys_dict_item")
public class SysDictItem extends BaseEntity {
    /**
     * 数据库主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属字典类型 ID
     */
    private Long dictTypeId;

    /**
     * 字典项显示文本
     */
    private String label;

    /**
     * 字典项存储值
     */
    private String value;

    /**
     * 字典项展示颜色
     */
    private String color;

    /**
     * 排序值；越小越靠前，相同值按 ID 升序
     */
    private Integer sort;

    /**
     * 是否为该字典类型的默认项
     */
    @TableField("is_default")
    private Boolean defaultItem;

    /**
     * 启用状态：1 启用，0 停用
     */
    private Integer status;

    /**
     * 业务备注
     */
    private String remark;
}
