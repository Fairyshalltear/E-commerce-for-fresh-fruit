package com.fairyshalltear.freshfruit.inventory.entity;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.*;

@TableName("sku_inventory")
public class SkuInventory {
    @TableId(type = IdType.INPUT)
    private Long skuId;
    private Integer onHand;
    private Integer reserved;
    private Integer lowStockThreshold;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long value) { skuId = value; }
    public Integer getOnHand() { return onHand; }
    public void setOnHand(Integer value) { onHand = value; }
    public Integer getReserved() { return reserved; }
    public void setReserved(Integer value) { reserved = value; }
    public Integer getLowStockThreshold() { return lowStockThreshold; }
    public void setLowStockThreshold(Integer value) { lowStockThreshold = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
}
