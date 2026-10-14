package com.maplehush.admin.system.dept.service;

import com.maplehush.admin.system.dept.dto.SysDeptSaveDTO;
import com.maplehush.admin.system.dept.vo.SysDeptVO;

import java.util.List;

/**
 * 系统部门管理
 */
public interface ISysDeptService {
    /**
     * 查询部门树
     *
     * @return 按层级和稳定顺序组装的部门树
     */
    List<SysDeptVO> listDepts();

    /**
     * 校验并创建部门
     *
     * @param dto 经过输入校验的业务表单；授权和数据库状态仍由业务层校验
     * @return 新建部门 ID
     * @throws com.maplehush.admin.common.exception.BusinessException 参数、授权范围、资源状态或关联约束不满足业务规则
     */
    Long saveDept(SysDeptSaveDTO dto);

    /**
     * 校验并修改部门，防止层级循环
     *
     * @param id 目标业务资源 ID
     * @param dto 经过输入校验的业务表单；授权和数据库状态仍由业务层校验
     * @throws com.maplehush.admin.common.exception.BusinessException 参数、授权范围、资源状态或关联约束不满足业务规则
     */
    void updateDept(Long id, SysDeptSaveDTO dto);

    /**
     * 删除无子部门且无关联用户的部门
     *
     * @param id 目标业务资源 ID
     * @throws com.maplehush.admin.common.exception.BusinessException 参数、授权范围、资源状态或关联约束不满足业务规则
     */
    void removeDept(Long id);
}
