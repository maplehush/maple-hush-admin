package com.maplehush.admin.system.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.maplehush.admin.system.dict.entity.SysDictType;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 字典类型表基础映射
 */
@Mapper
public interface SysDictTypeMapper extends BaseMapper<SysDictType> {
    /**
     * 默认项切换及类型删除共用的父行锁
     * @param id 类型 ID
     * @return 类型当前状态；不存在为空
     */
    @Select(
            "SELECT id,dict_code,dict_name,status,remark,create_time,update_time FROM sys_dict_type WHERE id=#{id} FOR UPDATE")
    SysDictType lockType(@Param("id") long id);

    /**
     * 显式更新可空备注，不依赖实体的非空更新策略
     * @param type 目标 ID、名称、状态和备注
     * @return 更新行数
     */
    @Update(
            "UPDATE sys_dict_type SET dict_name=#{dictName},status=#{status},remark=#{remark},update_time=CURRENT_TIMESTAMP WHERE id=#{id}")
    int updateType(SysDictType type);
}
