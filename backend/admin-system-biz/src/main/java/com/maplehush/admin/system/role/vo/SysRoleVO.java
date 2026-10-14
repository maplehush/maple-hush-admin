package com.maplehush.admin.system.role.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理角色响应；permissions 是菜单节点 ID，不是权限码
 *
 * @param id 业务资源 ID，以字符串返回，避免前端整数精度丢失
 * @param roleCode 稳定角色编码；创建时必填，修改时不能变更
 * @param name 业务显示名称
 * @param status 启用状态：1 启用，0 停用；表单为空时按创建或修改规则处理
 * @param builtIn 是否为受保护的内置角色
 * @param remark 业务备注
 * @param permissions 该角色已授予的菜单节点 ID 列表
 * @param createTime 创建时间，格式为东八区 yyyy-MM-dd HH:mm:ss
 * @param updateTime 最后更新时间，格式为东八区 yyyy-MM-dd HH:mm:ss
 */
public record SysRoleVO(
        String id,
        String roleCode,
        String name,
        Integer status,
        Boolean builtIn,
        String remark,
        List<String> permissions,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
