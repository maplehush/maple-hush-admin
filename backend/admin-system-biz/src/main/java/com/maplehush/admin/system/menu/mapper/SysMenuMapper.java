package com.maplehush.admin.system.menu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.maplehush.admin.system.menu.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;

/** 菜单与权限节点基础映射。 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {
}
