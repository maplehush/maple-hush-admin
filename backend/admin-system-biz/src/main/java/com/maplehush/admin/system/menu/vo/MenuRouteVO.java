package com.maplehush.admin.system.menu.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/** 当前用户动态路由；不暴露管理字段或按钮授权。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MenuRouteVO(String name, String path, String component, String redirect,
                          Map<String, Object> meta, List<MenuRouteVO> children) {}
