package com.maplehush.admin.system.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.maplehush.admin.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 字典类型。 */
@Getter
@Setter
@TableName("sys_dict_type")
public class SysDictType extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String dictCode;
    private String dictName;
    private Integer status;
    private String remark;
}
