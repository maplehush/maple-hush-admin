package com.maplehush.admin.system.user.service;

import com.maplehush.admin.common.api.PageResult;
import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.system.user.dto.SysUserCreateDTO;
import com.maplehush.admin.system.user.dto.SysUserQueryDTO;
import com.maplehush.admin.system.user.dto.SysUserRolesDTO;
import com.maplehush.admin.system.user.dto.SysUserUpdateDTO;
import com.maplehush.admin.system.user.vo.SysUserVO;

/**
 * 用户管理业务契约；唯一超管与角色范围保护由实现统一执行
 */
public interface ISysUserService {
    /**
     * 分页查询用户安全信息
     * @param query 已验证的筛选与分页条件
     * @return 当前页及符合条件的总条数
     */
    PageResult<SysUserVO> listUsers(SysUserQueryDTO query);

    /**
     * 查询用户详情
     * @param id 用户 ID
     * @return 不含敏感凭据的用户信息
     */
    SysUserVO getUser(Long id);

    /**
     * 创建必须改密的普通账号
     * @param dto 账号、资料及可选角色表单
     * @return 新建用户 ID
     * @throws BusinessException 账号唯一性、部门、角色或授权范围不满足要求
     */
    Long saveUser(SysUserCreateDTO dto);

    /**
     * 修改资料或状态；状态变化撤销目标会话
     * @param id 目标用户 ID
     * @param dto 资料全量表单
     * @throws BusinessException 用户不存在、超管状态受保护或目标超出管理范围
     */
    void updateUser(Long id, SysUserUpdateDTO dto);

    /**
     * 删除普通用户，并清理会话及关联，保留日志快照
     * @param id 目标用户 ID
     * @throws BusinessException 用户不存在、属于唯一超管或超出管理范围
     */
    void removeUser(Long id);

    /**
     * 全量替换普通用户角色并使旧会话失效
     * @param id 目标用户 ID
     * @param dto 全量角色表单；空列表表示清空
     * @throws BusinessException 目标或角色不可用、受保护或超出可授予范围
     */
    void assignRoles(Long id, SysUserRolesDTO dto);

    /**
     * 重置为约定临时密码，仅撤销目标用户会话
     * @param id 目标用户 ID；唯一超管不允许重置
     * @throws BusinessException 用户不存在、属于唯一超管或超出管理范围
     */
    void resetPassword(Long id);
}
