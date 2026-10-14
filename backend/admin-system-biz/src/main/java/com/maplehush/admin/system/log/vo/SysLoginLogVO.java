package com.maplehush.admin.system.log.vo;

import java.time.LocalDateTime;

/**
 * 登录事件分页输出，不携带凭据或请求体
 *
 * @param id 日志主键，输出为字符串
 * @param userId 可空用户 ID；账号删除后保留快照，输出为字符串
 * @param username 登录账号快照
 * @param eventType LOGIN、LOGOUT 或 REFRESH
 * @param success 事件成功与否
 * @param reasonCode 固定原因码，不含凭据
 * @param clientIp 连接来源地址，不信任转发头
 * @param userAgent 截断的客户端标识，不含请求体
 * @param traceId 请求关联标识
 * @param createTime 东八区秒级事件时间
 */
public record SysLoginLogVO(
        String id,
        String userId,
        String username,
        String eventType,
        Boolean success,
        String reasonCode,
        String clientIp,
        String userAgent,
        String traceId,
        LocalDateTime createTime) {}
