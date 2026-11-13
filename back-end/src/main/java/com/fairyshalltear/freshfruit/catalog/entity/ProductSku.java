package com.fairyshalltear.freshfruit.catalog.entity;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.*;

@TableName("product_sku")
public class ProductSku {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String specification;
    private BigDecimal price;
    private Integer status;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getProductId() { return productId; }
    public void setProductId(Long value) { productId = value; }
    public String getSpecification() { return specification; }
    public void setSpecification(String value) { specification = value; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal value) { price = value; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer value) { status = value; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer value) { sortOrder = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
}
