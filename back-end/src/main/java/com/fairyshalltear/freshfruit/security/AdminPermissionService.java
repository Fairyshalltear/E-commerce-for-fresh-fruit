package com.fairyshalltear.freshfruit.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.fairyshalltear.freshfruit.common.api.ApiException;

@Service
public class AdminPermissionService {
    private final JdbcTemplate jdbc;

    public AdminPermissionService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void requireOperation(Long accountId, String module, String operation) {
        String role = jdbc.queryForObject("SELECT account_type FROM admin_account WHERE id=? AND status=1", String.class, accountId);
        if ("OWNER".equals(role)) return;
        if (!"STAFF".equals(role) || !has(accountId, "MODULE", module) || !has(accountId, "OPERATION", operation)) {
            throw new ApiException("FORBIDDEN", "当前账户无权执行此敏感操作", HttpStatus.FORBIDDEN);
        }
    }

    private boolean has(Long id, String scope, String permission) {
        return !jdbc.query("SELECT permission_key FROM admin_permission WHERE admin_id=? AND permission_scope=? AND permission_key=?",
                (rs, row) -> rs.getString(1), id, scope, permission).isEmpty();
    }
}
