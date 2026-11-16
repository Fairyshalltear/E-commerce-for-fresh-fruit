package com.fairyshalltear.freshfruit.audit;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AdminWriteAuditInterceptor implements HandlerInterceptor {
    private final AuditService audit;

    public AdminWriteAuditInterceptor(AuditService audit) {
        this.audit = audit;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception exception) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/v1/admin/") || path.equals("/api/v1/admin/auth/login")
                || !isWrite(request.getMethod()) || AuditService.requestAlreadyRecorded()) return;

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long accountId = authentication instanceof JwtAuthenticationToken jwt
                ? parseId(jwt.getToken().getSubject()) : null;
        String result = response.getStatus() == 403 ? "DENIED"
                : response.getStatus() >= 400 || exception != null ? "FAILED" : "SUCCESS";
        audit.recordRequestOutcome(accountId, request.getMethod() + " " + path, "REQUEST", null,
                result, exception == null ? (response.getStatus() >= 400 ? "HTTP_" + response.getStatus() : null)
                        : exception.getClass().getSimpleName());
    }

    private boolean isWrite(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)
                || "DELETE".equals(method);
    }

    private Long parseId(String subject) {
        try { return Long.valueOf(subject); } catch (RuntimeException ignored) { return null; }
    }
}
