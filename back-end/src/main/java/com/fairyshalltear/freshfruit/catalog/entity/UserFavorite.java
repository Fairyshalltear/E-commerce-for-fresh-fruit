package com.fairyshalltear.freshfruit.catalog.entity;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.*;

@TableName("user_favorite")
public class UserFavorite {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long buyerId;
    private Long productId;
    private String productNameSnapshot;
    private String mainImageSnapshot;
    private LocalDateTime createdAt;
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long value) { buyerId = value; }
    public Long getProductId() { return productId; }
    public void setProductId(Long value) { productId = value; }
    public String getProductNameSnapshot() { return productNameSnapshot; }
    public void setProductNameSnapshot(String value) { productNameSnapshot = value; }
    public String getMainImageSnapshot() { return mainImageSnapshot; }
    public void setMainImageSnapshot(String value) { mainImageSnapshot = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
}
