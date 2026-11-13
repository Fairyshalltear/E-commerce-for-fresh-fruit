package com.fairyshalltear.freshfruit.catalog.entity;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.*;

@TableName("product_image")
public class ProductImage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String storageKey;
    private Long uploadedBy;
    private Long productId;
    private Integer active;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String value) { storageKey = value; }
    public Long getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(Long value) { uploadedBy = value; }
    public Long getProductId() { return productId; }
    public void setProductId(Long value) { productId = value; }
    public Integer getActive() { return active; }
    public void setActive(Integer value) { active = value; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer value) { sortOrder = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
}
