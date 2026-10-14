package com.maplehush.admin.system.role.service;

import com.maplehush.admin.common.api.PageResult;
import com.maplehush.admin.system.role.dto.SysRoleQueryDTO;
import com.maplehush.admin.system.role.dto.SysRoleSaveDTO;
import com.maplehush.admin.system.role.vo.SysRoleVO;

/** 系统角色和菜单授权管理。 */
public interface ISysRoleService {
    /** 按条件分页查询角色。 */
    PageResult<SysRoleVO> listRoles(SysRoleQueryDTO query);

    /** 查询角色详情和菜单授权。 */
    SysRoleVO getRole(Long id);

    /** 校验可授予范围并创建角色。 */
    Long saveRole(SysRoleSaveDTO dto);

    /** 修改角色及菜单授权，使原持有者授权失效。 */
    void updateRole(Long id, SysRoleSaveDTO dto);

    /** 删除非内置角色及其关联，使原持有者授权失效。 */
    void removeRole(Long id);
}
