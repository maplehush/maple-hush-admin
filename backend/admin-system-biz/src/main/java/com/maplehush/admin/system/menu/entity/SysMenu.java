package com.maplehush.admin.system.menu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.maplehush.admin.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 路由、页面与按钮共用的菜单节点。 */
@Getter
@Setter
@TableName("sys_menu")
public class SysMenu extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String name;
    private String title;
    private String type;
    private String path;
    private String component;
    private String redirect;
    private String authCode;
    private String icon;
    private String metaJson;
    private Integer sort;
    private Integer status;
}
