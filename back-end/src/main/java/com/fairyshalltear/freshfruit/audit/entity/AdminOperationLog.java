package com.fairyshalltear.freshfruit.audit.entity;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.*;

@TableName("admin_operation_log")
public class AdminOperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long adminId;
    private String operationType;
    private String targetType;
    private Long targetId;
    private String result;
    private LocalDateTime createdAt;
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getAdminId() { return adminId; }
    public void setAdminId(Long value) { adminId = value; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String value) { operationType = value; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String value) { targetType = value; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long value) { targetId = value; }
    public String getResult() { return result; }
    public void setResult(String value) { result = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
}
