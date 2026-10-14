package com.maplehush.admin.system.menu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.maplehush.admin.common.entity.BaseEntity;

import lombok.Getter;
import lombok.Setter;

/**
 * 路由、页面与按钮共用的菜单节点
 */
@Getter
@Setter
@TableName("sys_menu")
public class SysMenu extends BaseEntity {
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
     * 唯一的前端路由名称
     */
    private String name;

    /**
     * 菜单显示标题
     */
    private String title;

    /**
     * 菜单类型：catalog 目录、menu 页面、embedded 内嵌、link 外链、button 按钮
     */
    private String type;

    /**
     * 数据库内的相对路由路径；根菜单保存完整路径
     */
    private String path;

    /**
     * 已登记的前端页面组件键，不含 .vue 后缀
     */
    private String component;

    /**
     * 路由重定向目标
     */
    private String redirect;

    /**
     * 接口授权使用的小写权限码；目录不配置权限码
     */
    private String authCode;

    /**
     * 菜单图标键
     */
    private String icon;

    /**
     * 经过字段白名单校验的路由扩展元数据 JSON
     */
    private String metaJson;

    /**
     * 排序值；越小越靠前，相同值按 ID 升序
     */
    private Integer sort;

    /**
     * 启用状态：1 启用，0 停用
     */
    private Integer status;
}
