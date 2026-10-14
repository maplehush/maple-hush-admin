package com.maplehush.admin.system.auth.schedule;

import com.maplehush.admin.system.auth.mapper.AuthSessionMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 仅删除到期后超过留存期的记录，族内有效的已用或已撤销记录继续保留
 */
@Component
public class AuthSessionCleanup {
    private final AuthSessionMapper mapper;
    private final int retentionDays;

    public AuthSessionCleanup(
            AuthSessionMapper mapper,
            @Value("${maple-hush.auth.session-retention-days:7}") int retentionDays) {
        if (retentionDays < 0 || retentionDays > 365) {
            throw new IllegalArgumentException("会话留存天数应为 0～365");
        }
        this.mapper = mapper;
        this.retentionDays = retentionDays;
    }

    /**
     * 按批次清理超过留存期的会话；每次最多十批，避免维护任务长时间占用数据库
     *
     * @return 本轮实际删除的会话记录数
     */
    @Scheduled(
            fixedDelayString = "${maple-hush.auth.session-cleanup-delay-ms:3600000}",
            initialDelayString = "${maple-hush.auth.session-cleanup-delay-ms:3600000}")
    public int cleanupExpiredSessions() {
        LocalDateTime cutoff =
                LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusDays(retentionDays);
        int total = 0;
        for (int batch = 0; batch < 10; batch++) {
            int deleted = mapper.deleteExpiredBefore(cutoff);
            total += deleted;
            // Mapper 每批最多删除 1000 条；不足一批表示本轮已无更多可清理记录。
            if (deleted < 1000) {
                break;
            }
        }
        return total;
    }
}
