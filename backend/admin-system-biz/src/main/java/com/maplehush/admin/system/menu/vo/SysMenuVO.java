package com.maplehush.admin.system.menu.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * 菜单管理树含按钮、字符串 ID 和完整访问路径，与动态路由 VO 分开
 *
 * @param id 业务资源 ID，以字符串返回，避免前端整数精度丢失
 * @param pid 父节点 ID；根节点使用空值或协议约定的根值
 * @param name 唯一的前端路由名称
 * @param type 菜单类型：catalog、menu、embedded、link 或 button
 * @param status 启用状态：1 启用，0 停用；表单为空时按创建或修改规则处理
 * @param path 完整静态访问路径；外链路径由服务端按菜单名称生成
 * @param component 已登记的前端组件键，不含 .vue 后缀
 * @param redirect 路由重定向目标
 * @param authCode 小写接口权限码；目录不设置权限码
 * @param meta 通过白名单校验的路由元数据，含标题、图标和排序等展示信息
 * @param children 按稳定顺序排列的子节点；叶节点为空值，响应中省略该字段
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SysMenuVO(
        String id,
        String pid,
        String name,
        String type,
        Integer status,
        String path,
        String component,
        String redirect,
        String authCode,
        Map<String, Object> meta,
        List<SysMenuVO> children) {}
