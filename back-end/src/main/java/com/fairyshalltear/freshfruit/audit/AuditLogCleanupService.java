package com.fairyshalltear.freshfruit.audit;

import java.time.LocalDateTime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogCleanupService {
    private final JdbcTemplate jdbc;

    public AuditLogCleanupService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public int deleteExpiredBefore(LocalDateTime cutoff) {
        return jdbc.update("""
                DELETE FROM admin_operation_log
                WHERE created_at < ?
                  AND NOT EXISTS (
                    SELECT 1 FROM inventory_movement
                    WHERE inventory_movement.operation_log_id = admin_operation_log.id
                  )
                """, cutoff);
    }
}
