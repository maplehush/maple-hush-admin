package com.maplehush.admin.system.menu.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * 当前用户动态路由；不暴露管理字段或按钮授权
 *
 * @param name 唯一的前端路由名称
 * @param path 用于动态路由的相对子路径；根菜单使用完整路径
 * @param component 已登记的前端组件键，不含 .vue 后缀
 * @param redirect 路由重定向目标
 * @param meta 通过白名单校验的路由元数据，含标题、图标和排序等展示信息
 * @param children 按稳定顺序排列的子节点；叶节点为空值，响应中省略该字段
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MenuRouteVO(
        @Schema(description = "唯一的前端路由名称") String name,
        @Schema(description = "用于动态路由的相对子路径；根菜单使用完整路径") String path,
        @Schema(description = "已登记的前端组件键，不含 .vue 后缀") String component,
        @Schema(description = "路由重定向目标") String redirect,
        @Schema(description = "通过白名单校验的路由元数据，含标题、图标和排序等展示信息") Map<String, Object> meta,
        @Schema(description = "按稳定顺序排列的子节点；叶节点为空值，响应中省略该字段") List<MenuRouteVO> children) {}
