package com.fairyshalltear.freshfruit.audit;

import java.time.LocalDateTime;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fairyshalltear.freshfruit.account.entity.AdminAccount;
import com.fairyshalltear.freshfruit.account.mapper.AdminAccountMapper;
import com.fairyshalltear.freshfruit.audit.entity.AdminOperationLog;
import com.fairyshalltear.freshfruit.audit.mapper.AdminOperationLogMapper;

@Service
public class AuditService {
    private final AdminOperationLogMapper logs;

    private final AdminAccountMapper admins;

    public AuditService(AdminOperationLogMapper logs, AdminAccountMapper admins) {
        this.logs = logs;
        this.admins = admins;
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public Long record(Long admin, String operation, String targetType, Long target) {
        return recordWithSummary(admin, operation, targetType, target, targetSummary(targetType, target));
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public Long recordWithSummary(Long admin, String operation, String targetType, Long target, String summary) {
        AdminOperationLog log = new AdminOperationLog();
        log.setAdminId(admin);
        log.setOperationType(operation);
        log.setTargetType(targetType);
        log.setTargetId(target);
        log.setResult("SUCCESS");
        log.setEventCategory("BUSINESS");
        log.setActorUsernameSnapshot(username(admin));
        log.setTargetSummary(truncate(summary, 500));
        requestContext(log);
        logs.insert(log);
        markRequestRecordedAfterCommit();
        return log.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAuthentication(Long accountId, String username, boolean success, String failureCategory) {
        AdminOperationLog log = new AdminOperationLog();
        log.setAdminId(accountId);
        log.setOperationType("ADMIN_LOGIN");
        log.setTargetType(accountId == null ? null : "ADMIN_ACCOUNT");
        log.setTargetId(accountId);
        log.setResult(success ? "SUCCESS" : "FAILED");
        log.setEventCategory("AUTHENTICATION");
        log.setActorUsernameSnapshot(truncate(username, 50));
        log.setFailureCategory(success ? null : truncate(failureCategory, 80));
        requestContext(log);
        logs.insert(log);
        markRequestRecordedAfterCommit();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRequestOutcome(Long accountId, String operation, String targetType, Long targetId,
            String result, String failureCategory) {
        AdminOperationLog log = new AdminOperationLog();
        log.setAdminId(accountId);
        log.setOperationType(truncate(operation, 50));
        log.setTargetType(truncate(targetType, 30));
        log.setTargetId(targetId);
        log.setResult(truncate(result, 20));
        log.setEventCategory("BUSINESS");
        log.setActorUsernameSnapshot(username(accountId));
        log.setTargetSummary(targetSummary(targetType, targetId));
        log.setFailureCategory(truncate(failureCategory, 80));
        requestContext(log);
        logs.insert(log);
        markRequestRecordedAfterCommit();
    }

    public static boolean requestAlreadyRecorded() {
        ServletRequestAttributes attributes = currentRequest();
        return attributes != null && Boolean.TRUE.equals(
                attributes.getRequest().getAttribute("freshfruit.audit.recorded"));
    }

    private String username(Long accountId) {
        if (accountId == null) return null;
        AdminAccount account = admins.selectById(accountId);
        return account == null ? null : truncate(account.getUsername(), 50);
    }

    private void requestContext(AdminOperationLog log) {
        ServletRequestAttributes attributes = currentRequest();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            log.setSourceIp(truncate(request.getRemoteAddr(), 45));
            log.setRequestPath(truncate(request.getRequestURI(), 255));
        }
    }

    private void markRequestRecordedAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ServletRequestAttributes attributes = currentRequest();
                    if (attributes != null) attributes.getRequest().setAttribute("freshfruit.audit.recorded", true);
                }
            });
        }
    }

    private static ServletRequestAttributes currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes : null;
    }

    private static String targetSummary(String type, Long id) {
        if (type == null && id == null) return null;
        return truncate((type == null ? "目标" : type) + (id == null ? "" : " #" + id), 500);
    }

    private static String truncate(String value, int limit) {
        if (value == null) return null;
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
