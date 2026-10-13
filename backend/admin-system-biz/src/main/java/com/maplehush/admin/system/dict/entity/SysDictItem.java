package com.maplehush.admin.system.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.maplehush.admin.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 字典项；同类型单一默认项由业务事务维护。 */
@Getter
@Setter
@TableName("sys_dict_item")
public class SysDictItem extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long dictTypeId;
    private String label;
    private String value;
    private String color;
    private Integer sort;
    @TableField("is_default")
    private Boolean defaultItem;
    private Integer status;
    private String remark;
}
