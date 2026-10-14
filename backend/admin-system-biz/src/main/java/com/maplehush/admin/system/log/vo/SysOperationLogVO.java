package com.maplehush.admin.system.log.vo;

import java.time.LocalDateTime;

/**
 * 操作审计分页输出，不携带凭据或请求体
 *
 * @param id 日志主键，输出为字符串
 * @param actorUserId 可空操作人 ID；删除账号后保留快照，输出为字符串
 * @param actorUsername 操作人账号快照
 * @param operationCode 服务端注解定义的操作码
 * @param targetType 受控目标类型
 * @param targetId 显式指定的目标 ID
 * @param success 业务事务是否成功提交
 * @param summary 固定结果摘要，不保存入参或异常
 * @param requestMethod HTTP 请求方法
 * @param requestPath 服务端路由模板，不含路径实参或查询参数
 * @param clientIp 连接来源地址
 * @param traceId 请求关联标识
 * @param createTime 东八区秒级创建时间
 */
public record SysOperationLogVO(
        String id,
        String actorUserId,
        String actorUsername,
        String operationCode,
        String targetType,
        String targetId,
        Boolean success,
        String summary,
        String requestMethod,
        String requestPath,
        String clientIp,
        String traceId,
        LocalDateTime createTime) {}
