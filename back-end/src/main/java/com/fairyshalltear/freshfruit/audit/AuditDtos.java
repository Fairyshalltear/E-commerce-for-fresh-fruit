package com.fairyshalltear.freshfruit.audit;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public final class AuditDtos {
    private AuditDtos() {}

    public record Query(@Min(1) Integer page, @Min(1) @Max(100) Integer size,
            Long actorId, String category, String result, String target,
            String sourceIp, String requestPath, LocalDateTime from, LocalDateTime to) {
        public int pageNumber() { return page == null ? 1 : page; }
        public int pageSize() { return size == null ? 20 : size; }
    }

    public record LogView(Long id, Long actorId, String actorUsername, String category,
            String operation, String targetType, Long targetId, String targetSummary,
            String result, String failureCategory, String sourceIp, String requestPath,
            LocalDateTime createdAt) {}

    public record Page(List<LogView> items, long total, int page, int size) {}
}
