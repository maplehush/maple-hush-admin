package com.maplehush.admin.system.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.maplehush.admin.system.log.entity.SysLoginLog;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 登录审计分页与有界清理；保留账号快照，不关联当前用户表
 */
@Mapper
public interface SysLoginLogMapper extends BaseMapper<SysLoginLog> {
    /**
     * 按时间索引顺序删除一批过期记录
     * @param cutoff 严格早于该东八区时刻才允许删除
     * @return 本批删除数，最多 1000
     */
    @Delete(
            "DELETE FROM sys_login_log WHERE create_time < #{cutoff} ORDER BY create_time,id LIMIT 1000")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
