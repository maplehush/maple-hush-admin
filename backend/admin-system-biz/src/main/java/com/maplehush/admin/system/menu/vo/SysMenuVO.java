package com.maplehush.admin.system.menu.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/** 菜单管理树含按钮、字符串 ID 和完整访问路径，与动态路由 VO 分开。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SysMenuVO(String id, String pid, String name, String type, Integer status,
                        String path, String component, String redirect, String authCode,
                        Map<String, Object> meta, List<SysMenuVO> children) {}
