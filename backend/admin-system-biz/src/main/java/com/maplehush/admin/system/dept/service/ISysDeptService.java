package com.maplehush.admin.system.dept.service;

import com.maplehush.admin.system.dept.dto.SysDeptSaveDTO;
import com.maplehush.admin.system.dept.vo.SysDeptVO;
import java.util.List;

/** 系统部门管理。 */
public interface ISysDeptService {
    /** 查询部门树。 */
    List<SysDeptVO> listDepts();

    /** 校验并创建部门。 */
    Long saveDept(SysDeptSaveDTO dto);

    /** 校验并修改部门，防止层级循环。 */
    void updateDept(Long id, SysDeptSaveDTO dto);

    /** 删除无子部门且无关联用户的部门。 */
    void removeDept(Long id);
}
