package com.maplehush.admin.system.log.mapper;

import com.maplehush.admin.system.log.service.ISysOperationLogService.Entry;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/** 仅写入经服务端限定的审计字段，不接收原始请求参数。 */
@Mapper
public interface SysOperationLogMapper {
    @Insert("INSERT INTO sys_operation_log(actor_user_id,actor_username,operation_code,target_type,target_id,success,"
            + "summary,request_method,request_path,client_ip,trace_id) VALUES (#{actorId},#{actorUsername},#{operationCode},"
            + "#{targetType},#{targetId},#{success},#{summary},#{requestMethod},#{requestPath},#{clientIp},#{traceId})")
    int save(Entry entry);
}
