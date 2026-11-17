package com.fairyshalltear.freshfruit.security;


import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fairyshalltear.freshfruit.audit.AuditService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AdminAuthorizationInterceptor implements HandlerInterceptor {
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public AdminAuthorizationInterceptor(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        if (path.equals("/api/v1/admin/auth/login")) return true;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long id = auth instanceof JwtAuthenticationToken jwt ? parseId(jwt.getToken().getSubject()) : null;
        Account account = id == null ? null : jdbc.query(
                "SELECT account_type,status FROM admin_account WHERE id=?", rs -> rs.next()
                        ? new Account(rs.getString(1), rs.getInt(2)) : null, id);
        String denied = denialReason(request, path, id, account);
        if (denied == null) return true;

        audit.recordRequestOutcome(id, request.getMethod() + " " + path, "REQUEST", null,
                "DENIED", denied);
        response.setStatus(account == null || account.status() != 1 ? 401 : 403);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"" + (response.getStatus() == 401 ? "UNAUTHORIZED" : "FORBIDDEN")
                + "\",\"message\":\"当前账户无权执行此操作\",\"data\":null}");
        return false;
    }

    private String denialReason(HttpServletRequest request, String path, Long id, Account account) {
        if (id == null || account == null || account.status() != 1) return "ACCOUNT_INACTIVE";
        if ("OWNER".equals(account.type())) return null;
        if (!"STAFF".equals(account.type())) return "UNKNOWN_ACCOUNT_TYPE";

        String route = path.substring("/api/v1/admin/".length());
        if (route.equals("auth/me")) return null;
        if (route.startsWith("staff") || route.startsWith("audit-logs")
                || route.equals("ai/models") || route.startsWith("ai/models/")) return "OWNER_ONLY";

        String module = module(route);
        if (module == null) return "UNCLASSIFIED_ADMIN_ENDPOINT";
        boolean moduleAllowed = has(id, "MODULE", module);
        if ("product-images".equals(route) && !moduleAllowed) {
            moduleAllowed = has(id, "MODULE", "CONTENT");
        }
        if (!moduleAllowed) return "MODULE_PERMISSION_REQUIRED";

        String operation = sensitiveOperation(request, route);
        return operation != null && !has(id, "OPERATION", operation) ? "SENSITIVE_PERMISSION_REQUIRED" : null;
    }

    private String module(String route) {
        if (route.equals("ai/knowledge") || route.startsWith("ai/knowledge/")
                || route.equals("ai/conversations") || route.startsWith("ai/conversations/")
                || route.equals("ai/rules") || route.startsWith("ai/rules/")
                || route.equals("ai/statistics") || route.startsWith("ai/statistics/")) return "AI";
        if (route.startsWith("products/") || route.equals("products") || route.startsWith("categories")
                || route.startsWith("product-images") || route.startsWith("reviews")) return "PRODUCTS";
        if (route.startsWith("buyers")) return "BUYERS";
        if (route.startsWith("orders")) return "ORDERS";
        if (route.startsWith("inventory")) return "INVENTORY";
        if (route.startsWith("after-sales")) return "AFTER_SALES";
        if (route.startsWith("content")) return "CONTENT";
        if (route.startsWith("analytics")) return "STATISTICS";
        return null;
    }

    private String sensitiveOperation(HttpServletRequest request, String route) {
        if ("PATCH".equals(request.getMethod()) && route.matches("products/\\d+/status")) {
            return "PRODUCT_STATUS_CHANGE";
        }
        if (route.matches("inventory/stocks/\\d+/threshold")
                || route.matches("inventory/documents/\\d+/post")) return "INVENTORY_ADJUST";
        return null;
    }

    private boolean has(Long accountId, String scope, String key) {
        return !jdbc.query("SELECT admin_id FROM admin_permission WHERE admin_id=? AND permission_scope=? AND permission_key=?",
                (rs, row) -> rs.getLong(1), accountId, scope, key).isEmpty();
    }

    private Long parseId(String value) {
        try { return Long.valueOf(value); } catch (RuntimeException ignored) { return null; }
    }

    private record Account(String type, int status) {}
}
