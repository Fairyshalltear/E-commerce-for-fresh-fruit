package com.fairyshalltear.freshfruit.audit;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fairyshalltear.freshfruit.audit.entity.AdminOperationLog;
import com.fairyshalltear.freshfruit.audit.mapper.AdminOperationLogMapper;
import com.fairyshalltear.freshfruit.common.api.ApiException;

import static com.fairyshalltear.freshfruit.audit.AuditDtos.*;

@Service
public class AuditQueryService {
    private final AdminOperationLogMapper logs;

    public AuditQueryService(AdminOperationLogMapper logs) {
        this.logs = logs;
    }

    @Transactional(readOnly = true)
    public Page list(Query query) {
        validateRange(query);
        QueryWrapper<AdminOperationLog> wrapper = filters(query);
        long total = logs.selectCount(wrapper);
        long offset = (long) (query.pageNumber() - 1) * query.pageSize();
        List<LogView> items = logs.selectList(wrapper.clone().orderByDesc("created_at", "id")
                        .last("LIMIT " + query.pageSize() + " OFFSET " + offset))
                .stream().map(AuditQueryService::view).toList();
        return new Page(items, total, query.pageNumber(), query.pageSize());
    }

    @Transactional(readOnly = true)
    public LogView detail(Long id) {
        AdminOperationLog log = logs.selectById(id);
        if (log == null) throw new ApiException("NOT_FOUND", "审计记录不存在", HttpStatus.NOT_FOUND);
        return view(log);
    }

    private QueryWrapper<AdminOperationLog> filters(Query q) {
        QueryWrapper<AdminOperationLog> wrapper = new QueryWrapper<>();
        if (q.actorId() != null) wrapper.eq("admin_id", q.actorId());
        if (q.category() != null && !q.category().isBlank()) wrapper.eq("event_category", q.category().trim());
        if (q.result() != null && !q.result().isBlank()) wrapper.eq("result", q.result().trim());
        if (q.target() != null && !q.target().isBlank()) {
            String value = like(q.target().trim());
            wrapper.and(w -> w.like("target_summary", value).or().like("operation_type", value));
        }
        if (q.sourceIp() != null && !q.sourceIp().isBlank()) wrapper.like("source_ip", like(q.sourceIp().trim()));
        if (q.requestPath() != null && !q.requestPath().isBlank()) wrapper.like("request_path", like(q.requestPath().trim()));
        if (q.from() != null) wrapper.ge("created_at", q.from());
        if (q.to() != null) wrapper.le("created_at", q.to());
        return wrapper;
    }

    private void validateRange(Query q) {
        if (q.from() != null && q.to() != null && q.from().isAfter(q.to())) {
            throw new ApiException("VALIDATION_ERROR", "开始时间不能晚于结束时间", HttpStatus.BAD_REQUEST);
        }
    }

    private static LogView view(AdminOperationLog log) {
        return new LogView(log.getId(), log.getAdminId(), log.getActorUsernameSnapshot(),
                log.getEventCategory(), log.getOperationType(), log.getTargetType(), log.getTargetId(),
                log.getTargetSummary(), log.getResult(), log.getFailureCategory(), log.getSourceIp(),
                log.getRequestPath(), log.getCreatedAt());
    }

    private String like(String value) {
        return "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
