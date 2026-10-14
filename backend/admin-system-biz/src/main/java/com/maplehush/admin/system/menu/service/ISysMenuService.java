package com.maplehush.admin.system.menu.service;

import com.maplehush.admin.system.menu.dto.SysMenuSaveDTO;
import com.maplehush.admin.system.menu.vo.SysMenuVO;

import java.util.List;

/**
 * 系统菜单管理
 */
public interface ISysMenuService {
    /**
     * 查询菜单管理树
     *
     * @return 按层级和稳定顺序组装的菜单管理树
     */
    List<SysMenuVO> listMenus();

    /**
     * 检查菜单名称是否重复，可排除当前菜单
     *
     * @param name 菜单的唯一路由名称
     * @param excluded 查重时排除的当前菜单 ID；创建检查时为空
     * @return 是否存在未被排除的同名菜单
     */
    boolean nameExists(String name, Long excluded);

    /**
     * 检查完整访问路径是否重复，可排除当前菜单
     *
     * @param path 前端提交的完整静态访问路径
     * @param excluded 查重时排除的当前菜单 ID；创建检查时为空
     * @return 是否存在未被排除的相同完整路径
     */
    boolean pathExists(String path, Long excluded);

    /**
     * 校验并创建菜单，更新关联授权和审计
     *
     * @param dto 经过输入校验的业务表单；授权和数据库状态仍由业务层校验
     * @return 新建菜单 ID
     * @throws com.maplehush.admin.common.exception.BusinessException 参数、授权范围、资源状态或关联约束不满足业务规则
     */
    Long saveMenu(SysMenuSaveDTO dto);

    /**
     * 校验并修改菜单，使受影响用户授权失效
     *
     * @param id 目标业务资源 ID
     * @param dto 经过输入校验的业务表单；授权和数据库状态仍由业务层校验
     * @throws com.maplehush.admin.common.exception.BusinessException 参数、授权范围、资源状态或关联约束不满足业务规则
     */
    void updateMenu(Long id, SysMenuSaveDTO dto);

    /**
     * 校验并删除菜单及其授权关联
     *
     * @param id 目标业务资源 ID
     * @throws com.maplehush.admin.common.exception.BusinessException 参数、授权范围、资源状态或关联约束不满足业务规则
     */
    void removeMenu(Long id);
}
