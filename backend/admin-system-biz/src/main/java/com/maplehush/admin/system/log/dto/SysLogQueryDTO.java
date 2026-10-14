package com.maplehush.admin.system.log.dto;

import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Map;
import java.util.Set;

/**
 * 两类日志的只读查询；排序列只从白名单映射，时间区间左闭右开
 *
 * @param page 从 1 开始的页码
 * @param pageSize 每页 1～100 条
 * @param username 登录账号或操作人账号快照，精确匹配
 * @param eventType 登录事件筛选，操作日志为空
 * @param operationCode 操作码精确匹配，登录日志为空
 * @param success 是否成功，null 表示全部
 * @param traceId 请求追踪标识精确匹配
 * @param startTime 起始时刻，包含
 * @param endExclusive 结束上界，不包含；日期输入包括全天
 * @param sortBy id/createTime，默认 createTime
 * @param ascending 升序或降序，默认降序
 */
public record SysLogQueryDTO(
        int page,
        int pageSize,
        String username,
        String eventType,
        String operationCode,
        Boolean success,
        String traceId,
        LocalDateTime startTime,
        LocalDateTime endExclusive,
        String sortBy,
        boolean ascending) {
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
                    .withResolverStyle(ResolverStyle.STRICT);

    /**
     * 校验并规范化分页、筛选和日期边界
     * @param parameters HTTP 查询参数，不允许 SQL 字段或其他未知条件
     * @param login true 为登录日志，false 为操作日志
     * @return 已校验的查询条件
     */
    public static SysLogQueryDTO parse(Map<String, String> parameters, boolean login) {
        Set<String> allowed =
                login
                        ? Set.of(
                                "page",
                                "pageSize",
                                "username",
                                "eventType",
                                "success",
                                "traceId",
                                "startTime",
                                "endTime",
                                "sortBy",
                                "sortOrder")
                        : Set.of(
                                "page",
                                "pageSize",
                                "actorUsername",
                                "operationCode",
                                "success",
                                "traceId",
                                "startTime",
                                "endTime",
                                "sortBy",
                                "sortOrder");
        if (!allowed.containsAll(parameters.keySet())) {
            throw invalid();
        }
        int page = number(parameters.getOrDefault("page", "1"), Integer.MAX_VALUE);
        int size = number(parameters.getOrDefault("pageSize", "20"), 100);
        String sort = parameters.getOrDefault("sortBy", "createTime");
        String order = parameters.getOrDefault("sortOrder", "desc");
        if (!Set.of("id", "createTime").contains(sort) || !Set.of("asc", "desc").contains(order)) {
            throw invalid();
        }
        String event = text(parameters.get("eventType"), 32);
        if (event != null && !Set.of("LOGIN", "LOGOUT", "REFRESH").contains(event)) {
            throw invalid();
        }
        Boolean success = null;
        if (parameters.containsKey("success")) {
            success =
                    switch (parameters.get("success")) {
                        case "true", "1" -> true;
                        case "false", "0" -> false;
                        default -> throw invalid();
                    };
        }
        LocalDateTime start = time(parameters.get("startTime"), false);
        LocalDateTime end = time(parameters.get("endTime"), true);
        if (start != null && end != null && !start.isBefore(end)) {
            throw invalid();
        }
        return new SysLogQueryDTO(
                page,
                size,
                text(parameters.get(login ? "username" : "actorUsername"), 64),
                event,
                text(parameters.get("operationCode"), 120),
                success,
                text(parameters.get("traceId"), 64),
                start,
                end,
                sort,
                "asc".equals(order));
    }

    private static int number(String value, int max) {
        try {
            if (!value.matches("[1-9][0-9]{0,9}")) {
                throw invalid();
            }
            int parsed = Integer.parseInt(value);
            if (parsed > max) {
                throw invalid();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw invalid();
        }
    }

    private static String text(String value, int max) {
        if (value != null && value.length() > max) {
            throw invalid();
        }
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static LocalDateTime time(String value, boolean end) {
        if (value == null) {
            return null;
        }
        try {
            LocalDateTime result;
            if (value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                result = LocalDate.parse(value).atStartOfDay();
                if (end) {
                    result = result.plusDays(1);
                }
            } else if (value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}")) {
                result = LocalDateTime.parse(value, TIME);
                if (end) {
                    result = result.plusSeconds(1);
                }
            } else {
                throw invalid();
            }
            if (result.getYear() < 1000 || result.getYear() > 9999) {
                throw invalid();
            }
            return result;
        } catch (DateTimeException exception) {
            throw invalid();
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.INVALID_ARGUMENT, "日志分页、筛选或时间条件无效");
    }
}
