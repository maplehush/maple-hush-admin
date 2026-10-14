package com.maplehush.admin.system.permission.service;

import java.util.Collection;

/**
 * 业务事务内的授权版本变更和提交后缓存失效
 */
public interface IAuthorizationChangeService {
    /**
     * 在业务事务内递增角色持有者版本并安排缓存失效
     *
     * @param roleId 发生授权变更的角色 ID
     */
    void roleChanged(long roleId);

    /**
     * 在业务事务内使已授权用户的版本和缓存失效
     */
    void menusChanged();

    /**
     * 在业务事务内使指定用户的版本和缓存失效
     *
     * @param userIds 受影响用户 ID 集合；实现会去重并按 ID 排序
     */
    void usersChanged(Collection<Long> userIds);

    /**
     * 在业务提交后清除用户资料快照
     *
     * @param userId 目标用户 ID
     */
    void profileChanged(long userId);
}
