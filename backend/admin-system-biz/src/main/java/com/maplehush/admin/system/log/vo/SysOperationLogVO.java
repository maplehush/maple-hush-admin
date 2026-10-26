package com.maplehush.admin.system.log.vo;

import io.swagger.v3.oas.annotations.media.Schema;

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
 * @param requestPath 实际请求路径，含路径实参，不含查询参数；历史记录可能为路由模板
 * @param clientIp 连接来源地址
 * @param traceId 请求关联标识
 * @param createTime 东八区秒级创建时间
 */
public record SysOperationLogVO(
        @Schema(description = "日志主键，输出为字符串", example = "1") String id,
        @Schema(description = "可空操作人 ID；删除账号后保留快照，输出为字符串") String actorUserId,
        @Schema(description = "操作人账号快照") String actorUsername,
        @Schema(description = "服务端注解定义的操作码") String operationCode,
        @Schema(description = "受控目标类型") String targetType,
        @Schema(description = "显式指定的目标 ID") String targetId,
        @Schema(description = "业务事务是否成功提交") Boolean success,
        @Schema(description = "固定结果摘要，不保存入参或异常") String summary,
        @Schema(description = "HTTP 请求方法") String requestMethod,
        @Schema(description = "实际请求路径，含路径实参，不含查询参数；历史记录可能为路由模板") String requestPath,
        @Schema(description = "连接来源地址") String clientIp,
        @Schema(description = "请求关联标识") String traceId,
        @Schema(description = "东八区秒级创建时间") LocalDateTime createTime) {}
