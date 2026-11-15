package com.fairyshalltear.freshfruit.inventory.entity;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.*;

@TableName("inventory_movement")
public class InventoryMovement {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long skuId;
    private Long operationLogId;
    private String type;
    private Integer beforeOnHand;
    private Integer afterOnHand;
    private Integer quantityDelta;
    private String reason;
    private Long adminId;
    private LocalDateTime createdAt;
    private String sourceType;
    public String getSourceType() { return sourceType; }
    public void setSourceType(String value) { sourceType = value; }
    private Long sourceId;
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long value) { sourceId = value; }
    private Long sourceItemId;
    public Long getSourceItemId() { return sourceItemId; }
    public void setSourceItemId(Long value) { sourceItemId = value; }
    private String sourceNo;
    public String getSourceNo() { return sourceNo; }
    public void setSourceNo(String value) { sourceNo = value; }
    private String productNameSnapshot;
    public String getProductNameSnapshot() { return productNameSnapshot; }
    public void setProductNameSnapshot(String value) { productNameSnapshot = value; }
    private String specificationSnapshot;
    public String getSpecificationSnapshot() { return specificationSnapshot; }
    public void setSpecificationSnapshot(String value) { specificationSnapshot = value; }
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long value) { skuId = value; }
    public Long getOperationLogId() { return operationLogId; }
    public void setOperationLogId(Long value) { operationLogId = value; }
    public String getType() { return type; }
    public void setType(String value) { type = value; }
    public Integer getBeforeOnHand() { return beforeOnHand; }
    public void setBeforeOnHand(Integer value) { beforeOnHand = value; }
    public Integer getAfterOnHand() { return afterOnHand; }
    public void setAfterOnHand(Integer value) { afterOnHand = value; }
    public Integer getQuantityDelta() { return quantityDelta; }
    public void setQuantityDelta(Integer value) { quantityDelta = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public Long getAdminId() { return adminId; }
    public void setAdminId(Long value) { adminId = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
}
