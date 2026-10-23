package com.maplehush.admin.system.role.service;

import com.maplehush.admin.common.api.PageResult;
import com.maplehush.admin.system.role.dto.SysRoleQueryDTO;
import com.maplehush.admin.system.role.dto.SysRoleSaveDTO;
import com.maplehush.admin.system.role.vo.SysRoleVO;

/**
 * 系统角色和菜单授权管理
 */
public interface ISysRoleService {
    /**
     * 按条件分页查询角色
     *
     * @param query 已解析的分页、名称、状态和时间范围条件
     * @return 符合查询条件的当前页角色及总数
     */
    PageResult<SysRoleVO> listRoles(SysRoleQueryDTO query);

    /**
     * 查询角色详情和菜单授权
     *
     * @param id 目标业务资源 ID
     * @return 角色详情及其菜单授权 ID
     */
    SysRoleVO getRole(Long id);

    /**
     * 校验可授予范围并创建角色
     *
     * @param dto 经过输入校验的业务表单；授权和数据库状态仍由业务层校验
     * @return 新建角色 ID
     * @throws com.maplehush.admin.common.exception.BusinessException 参数、授权范围、资源状态或关联约束不满足业务规则
     */
    Long saveRole(SysRoleSaveDTO dto);

    /**
     * 修改角色及菜单授权，使原持有者授权失效
     *
     * @param id 目标业务资源 ID
     * @param dto 经过输入校验的业务表单；授权和数据库状态仍由业务层校验
     * @throws com.maplehush.admin.common.exception.BusinessException 参数、授权范围、资源状态或关联约束不满足业务规则
     */
    void updateRole(Long id, SysRoleSaveDTO dto);

    /**
     * 修改非内置角色状态并使原持有者授权失效
     *
     * @param id 目标角色 ID
     * @param status 启用状态：1 启用，0 停用
     * @throws com.maplehush.admin.common.exception.BusinessException 角色不存在、为内置角色或操作者不满足启用授权要求
     */
    void updateRoleStatus(Long id, Integer status);

    /**
     * 删除非内置角色及其关联，使原持有者授权失效
     *
     * @param id 目标业务资源 ID
     * @throws com.maplehush.admin.common.exception.BusinessException 参数、授权范围、资源状态或关联约束不满足业务规则
     */
    void removeRole(Long id);
}
