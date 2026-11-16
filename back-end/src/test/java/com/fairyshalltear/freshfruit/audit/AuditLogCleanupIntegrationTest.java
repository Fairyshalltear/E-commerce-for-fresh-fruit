package com.fairyshalltear.freshfruit.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auditcleanup;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false", "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:test-schema.sql",
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "security.admin.initial-username=cleanupowner", "security.admin.initial-password=OwnerPassword123!"
})
@AutoConfigureMockMvc
class AuditLogCleanupIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AuditLogCleanupService cleanup;

    @Test
    void deletesOnlyUnreferencedLogsOlderThanTheSevenDayCutoff() {
        long ownerId = jdbc.queryForObject("SELECT id FROM admin_account WHERE username='cleanupowner'", Long.class);
        jdbc.update("INSERT INTO product_category(name,parent_id) VALUES('清理测试分类',NULL)");
        long categoryId = jdbc.queryForObject("SELECT MAX(id) FROM product_category", Long.class);
        jdbc.update("INSERT INTO fruit_product(category_id,name) VALUES(?, '清理测试商品')", categoryId);
        long productId = jdbc.queryForObject("SELECT MAX(id) FROM fruit_product", Long.class);
        jdbc.update("INSERT INTO product_sku(product_id,specification,price) VALUES(?, '单件', 10)", productId);
        long skuId = jdbc.queryForObject("SELECT MAX(id) FROM product_sku", Long.class);
        LocalDateTime cutoff = LocalDateTime.of(2026, 11, 9, 3, 0);
        long expiredId = addLog(ownerId, cutoff.minusSeconds(1));
        long boundaryId = addLog(ownerId, cutoff);
        long referencedId = addLog(ownerId, cutoff.minusDays(1));
        jdbc.update("""
                INSERT INTO inventory_movement(sku_id,operation_log_id,type,before_on_hand,after_on_hand,
                    quantity_delta,reason,admin_id)
                VALUES(?,?,'ADJUSTMENT',0,1,1,'清理保留验证',?)
                """, skuId, referencedId, ownerId);

        assertEquals(1, cleanup.deleteExpiredBefore(cutoff));
        assertEquals(0, countLog(expiredId));
        assertEquals(1, countLog(boundaryId));
        assertEquals(1, countLog(referencedId));
    }

    private long addLog(long ownerId, LocalDateTime createdAt) {
        jdbc.update("""
                INSERT INTO admin_operation_log(admin_id,operation_type,result,created_at)
                VALUES(?,'CLEANUP_TEST','SUCCESS',?)
                """, ownerId, createdAt);
        return jdbc.queryForObject("SELECT MAX(id) FROM admin_operation_log", Long.class);
    }

    private int countLog(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM admin_operation_log WHERE id=?", Integer.class, id);
    }
}
