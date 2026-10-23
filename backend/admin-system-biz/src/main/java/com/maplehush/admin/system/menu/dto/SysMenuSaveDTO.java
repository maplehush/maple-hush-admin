package com.maplehush.admin.system.menu.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 管理表单采用完整路径；title/icon/order 由 meta 映射至独立列
 *
 * @param pid 父节点 ID；根节点使用空值或协议约定的根值
 * @param name 唯一的前端路由名称
 * @param type 菜单类型：catalog、menu、embedded、link 或 button
 * @param path 完整静态访问路径；外链可省略并由服务端按菜单名称生成，其他路由保存时转换为相对子路径
 * @param component 已登记的前端组件键，不含 .vue 后缀
 * @param redirect 路由重定向目标
 * @param authCode 小写接口权限码；目录不设置权限码
 * @param status 启用状态：1 启用，0 停用；表单为空时按创建或修改规则处理
 * @param meta 通过白名单校验的路由元数据，含标题、图标和排序等展示信息
 */
public record SysMenuSaveDTO(
        String pid,
        @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{0,99}") String name,
        @NotBlank String type,
        @Size(max = 255) String path,
        @Size(max = 255) String component,
        @Size(max = 255) String redirect,
        @Size(max = 120) String authCode,
        @Min(0) @Max(1) Integer status,
        Map<String, Object> meta) {}
