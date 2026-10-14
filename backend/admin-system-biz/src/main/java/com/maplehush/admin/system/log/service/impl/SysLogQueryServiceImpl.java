package com.maplehush.admin.system.log.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.maplehush.admin.common.api.PageResult;
import com.maplehush.admin.system.log.dto.SysLogQueryDTO;
import com.maplehush.admin.system.log.entity.SysLoginLog;
import com.maplehush.admin.system.log.entity.SysOperationLog;
import com.maplehush.admin.system.log.mapper.SysLoginLogMapper;
import com.maplehush.admin.system.log.mapper.SysOperationLogMapper;
import com.maplehush.admin.system.log.service.ISysLogQueryService;
import com.maplehush.admin.system.log.vo.SysLoginLogVO;
import com.maplehush.admin.system.log.vo.SysOperationLogVO;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 日志分页与显式 VO 转换；不联表改变历史账号快照
 */
@Service
public class SysLogQueryServiceImpl implements ISysLogQueryService {
    private final SysLoginLogMapper login;
    private final SysOperationLogMapper operation;

    public SysLogQueryServiceImpl(SysLoginLogMapper login, SysOperationLogMapper operation) {
        this.login = login;
        this.operation = operation;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<SysLoginLogVO> loginLogs(SysLogQueryDTO query) {
        var wrapper =
                Wrappers.<SysLoginLog>lambdaQuery()
                        .eq(query.username() != null, SysLoginLog::getUsername, query.username())
                        .eq(query.eventType() != null, SysLoginLog::getEventType, query.eventType())
                        .eq(query.success() != null, SysLoginLog::getSuccess, query.success())
                        .eq(query.traceId() != null, SysLoginLog::getTraceId, query.traceId())
                        .ge(
                                query.startTime() != null,
                                SysLoginLog::getCreateTime,
                                query.startTime())
                        .lt(
                                query.endExclusive() != null,
                                SysLoginLog::getCreateTime,
                                query.endExclusive())
                        .orderBy(
                                true,
                                query.ascending(),
                                "id".equals(query.sortBy())
                                        ? SysLoginLog::getId
                                        : SysLoginLog::getCreateTime);
        if (!"id".equals(query.sortBy())) {
            wrapper.orderBy(true, query.ascending(), SysLoginLog::getId);
        }
        var page = login.selectPage(new Page<SysLoginLog>(query.page(), query.pageSize()), wrapper);
        return new PageResult<>(
                page.getRecords().stream()
                        .map(
                                row ->
                                        new SysLoginLogVO(
                                                id(row.getId()),
                                                id(row.getUserId()),
                                                row.getUsername(),
                                                row.getEventType(),
                                                row.getSuccess(),
                                                row.getReasonCode(),
                                                row.getClientIp(),
                                                row.getUserAgent(),
                                                row.getTraceId(),
                                                row.getCreateTime()))
                        .toList(),
                page.getTotal());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<SysOperationLogVO> operationLogs(SysLogQueryDTO query) {
        var wrapper =
                Wrappers.<SysOperationLog>lambdaQuery()
                        .eq(
                                query.username() != null,
                                SysOperationLog::getActorUsername,
                                query.username())
                        .eq(
                                query.operationCode() != null,
                                SysOperationLog::getOperationCode,
                                query.operationCode())
                        .eq(query.success() != null, SysOperationLog::getSuccess, query.success())
                        .eq(query.traceId() != null, SysOperationLog::getTraceId, query.traceId())
                        .ge(
                                query.startTime() != null,
                                SysOperationLog::getCreateTime,
                                query.startTime())
                        .lt(
                                query.endExclusive() != null,
                                SysOperationLog::getCreateTime,
                                query.endExclusive())
                        .orderBy(
                                true,
                                query.ascending(),
                                "id".equals(query.sortBy())
                                        ? SysOperationLog::getId
                                        : SysOperationLog::getCreateTime);
        if (!"id".equals(query.sortBy())) {
            wrapper.orderBy(true, query.ascending(), SysOperationLog::getId);
        }
        var page =
                operation.selectPage(
                        new Page<SysOperationLog>(query.page(), query.pageSize()), wrapper);
        // 历史 summary 也不直接输出，避免旧版本或外部数据带出敏感请求内容。
        return new PageResult<>(
                page.getRecords().stream()
                        .map(
                                row ->
                                        new SysOperationLogVO(
                                                id(row.getId()),
                                                id(row.getActorUserId()),
                                                row.getActorUsername(),
                                                row.getOperationCode(),
                                                row.getTargetType(),
                                                row.getTargetId(),
                                                row.getSuccess(),
                                                Boolean.TRUE.equals(row.getSuccess())
                                                        ? "操作成功"
                                                        : "操作失败",
                                                row.getRequestMethod(),
                                                row.getRequestPath(),
                                                row.getClientIp(),
                                                row.getTraceId(),
                                                row.getCreateTime()))
                        .toList(),
                page.getTotal());
    }

    private String id(Long value) {
        return value == null ? null : value.toString();
    }
}
