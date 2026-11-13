package com.fairyshalltear.freshfruit.catalog.entity;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.*;

@TableName("fruit_product")
public class FruitProduct {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long categoryId;
    private String name;
    private String description;
    private String origin;
    private String taste;
    private String storageMethod;
    private Integer status;
    private Integer isRecommended;
    private Integer recommendSort;
    private LocalDateTime publishedAt;
    private Integer deleted;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long value) { categoryId = value; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public String getOrigin() { return origin; }
    public void setOrigin(String value) { origin = value; }
    public String getTaste() { return taste; }
    public void setTaste(String value) { taste = value; }
    public String getStorageMethod() { return storageMethod; }
    public void setStorageMethod(String value) { storageMethod = value; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer value) { status = value; }
    public Integer getIsRecommended() { return isRecommended; }
    public void setIsRecommended(Integer value) { isRecommended = value; }
    public Integer getRecommendSort() { return recommendSort; }
    public void setRecommendSort(Integer value) { recommendSort = value; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime value) { publishedAt = value; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer value) { deleted = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
}
