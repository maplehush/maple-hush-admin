package com.maplehush.admin.system.log.vo;

import io.swagger.v3.oas.annotations.media.Schema;

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
        @Schema(description = "日志主键，输出为字符串", example = "1") String id,
        @Schema(description = "可空用户 ID；账号删除后保留快照，输出为字符串", example = "1") String userId,
        @Schema(description = "登录账号快照") String username,
        @Schema(description = "LOGIN、LOGOUT 或 REFRESH") String eventType,
        @Schema(description = "事件成功与否") Boolean success,
        @Schema(description = "固定原因码，不含凭据") String reasonCode,
        @Schema(description = "连接来源地址，不信任转发头") String clientIp,
        @Schema(description = "截断的客户端标识，不含请求体") String userAgent,
        @Schema(description = "请求关联标识") String traceId,
        @Schema(description = "东八区秒级事件时间") LocalDateTime createTime) {}
