package com.maplehush.admin.system.menu.service;

import com.maplehush.admin.system.menu.dto.SysMenuSaveDTO;
import com.maplehush.admin.system.menu.vo.SysMenuVO;
import java.util.List;

/** 系统菜单管理。 */
public interface ISysMenuService {
    /** 查询菜单管理树。 */
    List<SysMenuVO> listMenus();

    /** 检查菜单名称是否重复，可排除当前菜单。 */
    boolean nameExists(String name, Long excluded);

    /** 检查完整访问路径是否重复，可排除当前菜单。 */
    boolean pathExists(String path, Long excluded);

    /** 校验并创建菜单，更新关联授权和审计。 */
    Long saveMenu(SysMenuSaveDTO dto);

    /** 校验并修改菜单，使受影响用户授权失效。 */
    void updateMenu(Long id, SysMenuSaveDTO dto);

    /** 校验并删除菜单及其授权关联。 */
    void removeMenu(Long id);
}
