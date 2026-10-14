package com.maplehush.admin.system.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.maplehush.admin.system.user.entity.SysUser;

import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表基础映射；业务读取须控制密码字段暴露
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {}
