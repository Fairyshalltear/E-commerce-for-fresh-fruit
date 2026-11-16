package com.fairyshalltear.freshfruit.account;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.fairyshalltear.freshfruit.audit.AuditService;
import com.fairyshalltear.freshfruit.common.api.ApiException;
import com.fairyshalltear.freshfruit.common.api.ApiResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/admin/staff")
@Validated
public class AdminStaffController {
    private static final Set<String> MODULES = Set.of("PRODUCTS", "BUYERS", "ORDERS", "INVENTORY",
            "AFTER_SALES", "CONTENT", "STATISTICS");
    private static final Set<String> OPERATIONS = Set.of("REFUND_EXECUTE", "PRODUCT_STATUS_CHANGE", "INVENTORY_ADJUST");

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final AuditService audit;

    public AdminStaffController(JdbcTemplate jdbc, PasswordEncoder passwords, AuditService audit) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<List<StaffView>> list() {
        List<StaffView> staff = new ArrayList<>();
        jdbc.query("SELECT id,username,display_name,status FROM admin_account WHERE account_type='STAFF' ORDER BY id",
                (org.springframework.jdbc.core.ResultSetExtractor<Void>) rs -> {
                    while (rs.next()) {
                        long id = rs.getLong("id");
                        staff.add(new StaffView(id, rs.getString("username"), rs.getString("display_name"),
                                rs.getInt("status") == 1, permissions(id, "MODULE"), permissions(id, "OPERATION")));
                    }
                    return null;
                });
        return ApiResponse.success(staff);
    }

    @PostMapping
    @Transactional
    public ApiResponse<StaffView> create(JwtAuthenticationToken auth, @Valid @RequestBody CreateRequest request) {
        validatePermissions(request.modulePermissions(), MODULES);
        validatePermissions(request.sensitivePermissions(), OPERATIONS);
        validatePassword(request.password());
        jdbc.update("INSERT INTO admin_account(username,password_hash,display_name,status,account_type,credential_version) VALUES(?,?,?,1,'STAFF',0)",
                request.username().trim(), passwords.encode(request.password()), cleanDisplayName(request.displayName()));
        Long id = jdbc.queryForObject("SELECT id FROM admin_account WHERE username=?", Long.class, request.username().trim());
        replacePermissions(id, request.modulePermissions(), request.sensitivePermissions());
        audit.record(ownerId(auth), "STAFF_CREATE", "STAFF_ACCOUNT", id);
        return ApiResponse.success(get(id));
    }

    @PutMapping("/{id}")
    @Transactional
    public ApiResponse<StaffView> update(JwtAuthenticationToken auth, @PathVariable Long id,
            @Valid @RequestBody UpdateRequest request) {
        requireStaff(id);
        validatePermissions(request.modulePermissions(), MODULES);
        validatePermissions(request.sensitivePermissions(), OPERATIONS);
        jdbc.update("UPDATE admin_account SET display_name=?,credential_version=credential_version+1 WHERE id=?",
                cleanDisplayName(request.displayName()), id);
        replacePermissions(id, request.modulePermissions(), request.sensitivePermissions());
        audit.recordWithSummary(ownerId(auth), "STAFF_PERMISSIONS_UPDATE", "STAFF_ACCOUNT", id,
                "模块权限=" + String.join(",", request.modulePermissions())
                        + "; 敏感操作权限=" + String.join(",", request.sensitivePermissions()));
        return ApiResponse.success(get(id));
    }

    @PatchMapping("/{id}/enabled")
    @Transactional
    public ApiResponse<StaffView> enabled(JwtAuthenticationToken auth, @PathVariable Long id,
            @Valid @RequestBody EnabledRequest request) {
        requireStaff(id);
        jdbc.update("UPDATE admin_account SET status=?,credential_version=credential_version+1 WHERE id=?",
                request.enabled() ? 1 : 0, id);
        audit.record(ownerId(auth), request.enabled() ? "STAFF_ENABLE" : "STAFF_DISABLE", "STAFF_ACCOUNT", id);
        return ApiResponse.success(get(id));
    }

    @PostMapping("/{id}/credentials")
    @Transactional
    public ApiResponse<StaffView> resetCredentials(JwtAuthenticationToken auth, @PathVariable Long id,
            @Valid @RequestBody ResetCredentialsRequest request) {
        requireStaff(id);
        validatePassword(request.password());
        jdbc.update("UPDATE admin_account SET password_hash=?,credential_version=credential_version+1 WHERE id=?",
                passwords.encode(request.password()), id);
        audit.record(ownerId(auth), "STAFF_CREDENTIAL_RESET", "STAFF_ACCOUNT", id);
        return ApiResponse.success(get(id));
    }

    private StaffView get(Long id) {
        return jdbc.queryForObject("SELECT id,username,display_name,status FROM admin_account WHERE id=? AND account_type='STAFF'",
                (rs, row) -> new StaffView(rs.getLong("id"), rs.getString("username"), rs.getString("display_name"),
                        rs.getInt("status") == 1, permissions(id, "MODULE"), permissions(id, "OPERATION")), id);
    }

    private List<String> permissions(Long id, String scope) {
        return jdbc.query("SELECT permission_key FROM admin_permission WHERE admin_id=? AND permission_scope=? ORDER BY permission_key",
                (rs, row) -> rs.getString(1), id, scope);
    }

    private void replacePermissions(Long id, List<String> modules, List<String> operations) {
        jdbc.update("DELETE FROM admin_permission WHERE admin_id=?", id);
        modules.forEach(key -> jdbc.update("INSERT INTO admin_permission(admin_id,permission_scope,permission_key) VALUES(?,'MODULE',?)", id, key));
        operations.forEach(key -> jdbc.update("INSERT INTO admin_permission(admin_id,permission_scope,permission_key) VALUES(?,'OPERATION',?)", id, key));
    }

    private void requireStaff(Long id) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM admin_account WHERE id=? AND account_type='STAFF'", Integer.class, id) == 0) {
            throw new ApiException("NOT_FOUND", "店员账号不存在", HttpStatus.NOT_FOUND);
        }
    }

    private void validatePermissions(List<String> values, Set<String> allowed) {
        if (values == null || values.stream().anyMatch(value -> value == null || !allowed.contains(value))
                || new HashSet<>(values).size() != values.size()) {
            throw new ApiException("VALIDATION_ERROR", "权限列表包含无效或重复项", HttpStatus.BAD_REQUEST);
        }
    }

    private void validatePassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length < 10 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException("VALIDATION_ERROR", "密码长度需为 10 至 72 个 UTF-8 字节", HttpStatus.BAD_REQUEST);
        }
    }

    private String cleanDisplayName(String value) {
        if (value == null || value.isBlank()) throw new ApiException("VALIDATION_ERROR", "显示名称不能为空", HttpStatus.BAD_REQUEST);
        return value.trim();
    }

    private Long ownerId(JwtAuthenticationToken auth) { return Long.valueOf(auth.getToken().getSubject()); }

    private record StaffView(Long id, String username, String displayName, boolean enabled,
            List<String> modulePermissions, List<String> sensitivePermissions) {}
    public record CreateRequest(@NotBlank @Size(max = 50) String username, @NotBlank @Size(max = 50) String displayName,
            @NotBlank String password, @NotNull List<String> modulePermissions, @NotNull List<String> sensitivePermissions) {}
    public record UpdateRequest(@NotBlank @Size(max = 50) String displayName,
            @NotNull List<String> modulePermissions, @NotNull List<String> sensitivePermissions) {}
    public record EnabledRequest(@NotNull Boolean enabled) {}
    public record ResetCredentialsRequest(@NotBlank String password) {}
}
