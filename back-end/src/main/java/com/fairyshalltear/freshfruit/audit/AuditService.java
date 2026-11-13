package com.fairyshalltear.freshfruit.audit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.fairyshalltear.freshfruit.audit.entity.AdminOperationLog;
import com.fairyshalltear.freshfruit.audit.mapper.AdminOperationLogMapper;
@Service
public class AuditService {
    private final AdminOperationLogMapper logs;
    public AuditService(AdminOperationLogMapper logs) { this.logs=logs; }
    @Transactional(propagation=Propagation.MANDATORY)
    public Long record(Long admin, String operation, String targetType, Long target) {
        AdminOperationLog log=new AdminOperationLog();
        log.setAdminId(admin); log.setOperationType(operation); log.setTargetType(targetType);
        log.setTargetId(target); log.setResult("SUCCESS"); logs.insert(log); return log.getId();
    }
}
