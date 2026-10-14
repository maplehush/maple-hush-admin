package com.maplehush.admin.system.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.maplehush.admin.system.dict.entity.SysDictItem;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 字典项表基础映射
 */
@Mapper
public interface SysDictItemMapper extends BaseMapper<SysDictItem> {
    /**
     * 先锁所属类型，再锁目标项，保持默认切换与删除的统一顺序
     * @param id 字典项 ID
     * @return 当前字典项；不存在为空
     */
    @Select(
            "SELECT id,dict_type_id,label,value,color,sort,is_default AS defaultItem,status,remark,create_time,update_time FROM sys_dict_item WHERE id=#{id} FOR UPDATE")
    SysDictItem lockItem(@Param("id") long id);

    /**
     * 清除同类型旧默认项；必须在持有类型行锁的业务事务内调用
     * @param typeId 所属类型 ID
     * @return 实际清除数
     */
    @Update(
            "UPDATE sys_dict_item SET is_default=0,update_time=CURRENT_TIMESTAMP WHERE dict_type_id=#{typeId} AND is_default=1")
    int clearDefaults(@Param("typeId") long typeId);

    /**
     * 全量更新资料并显式清除空颜色/备注，所属类型不参与更新
     * @param item 已校验的字典项资料
     * @return 更新行数
     */
    @Update(
            "UPDATE sys_dict_item SET label=#{label},value=#{value},color=#{color},sort=#{sort},is_default=#{defaultItem},status=#{status},remark=#{remark},update_time=CURRENT_TIMESTAMP WHERE id=#{id}")
    int updateItem(SysDictItem item);
}
