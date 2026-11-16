package com.fairyshalltear.freshfruit.audit;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuditLogCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(AuditLogCleanupJob.class);
    private static final ZoneId STORE_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int RETENTION_DAYS = 7;

    private final AuditLogCleanupService cleanup;

    public AuditLogCleanupJob(AuditLogCleanupService cleanup) {
        this.cleanup = cleanup;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Shanghai")
    public void cleanExpiredLogs() {
        LocalDateTime cutoff = LocalDateTime.now(STORE_ZONE).minusDays(RETENTION_DAYS);
        try {
            int deleted = cleanup.deleteExpiredBefore(cutoff);
            if (deleted > 0) log.info("Deleted {} expired audit logs before {}", deleted, cutoff);
        } catch (RuntimeException exception) {
            log.error("Audit log cleanup failed", exception);
        }
    }
}
