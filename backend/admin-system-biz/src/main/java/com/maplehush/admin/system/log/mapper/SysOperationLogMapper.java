package com.maplehush.admin.system.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.maplehush.admin.system.log.entity.SysOperationLog;
import com.maplehush.admin.system.log.service.ISysOperationLogService.Entry;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 操作审计写入、分页和有界清理，不接收原始请求参数
 */
@Mapper
public interface SysOperationLogMapper extends BaseMapper<SysOperationLog> {
    @Insert(
            "INSERT INTO sys_operation_log(actor_user_id,actor_username,operation_code,target_type,target_id,success,"
                    + "summary,request_method,request_path,client_ip,trace_id) VALUES (#{actorId},#{actorUsername},#{operationCode},"
                    + "#{targetType},#{targetId},#{success},#{summary},#{requestMethod},#{requestPath},#{clientIp},#{traceId})")
    int save(Entry entry);

    /**
     * 按时间索引顺序删除一批过期记录
     * @param cutoff 严格早于该东八区时刻才允许删除
     * @return 本批删除数，最多 1000
     */
    @Delete(
            "DELETE FROM sys_operation_log WHERE create_time < #{cutoff} ORDER BY create_time,id LIMIT 1000")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
