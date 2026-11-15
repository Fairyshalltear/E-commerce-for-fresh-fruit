package com.fairyshalltear.freshfruit.review.entity;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.*;

@TableName("product_review")
public class ProductReview {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private Long skuId;
    private Long buyerId;
    private Long orderItemId;
    private Integer rating;
    private String content;
    private String specificationSnapshot;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
    private Long deletedBy;
    private String deletionReason;
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getProductId() { return productId; }
    public void setProductId(Long value) { productId = value; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long value) { skuId = value; }
    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long value) { buyerId = value; }
    public Long getOrderItemId() { return orderItemId; }
    public void setOrderItemId(Long value) { orderItemId = value; }
    public Integer getRating() { return rating; }
    public void setRating(Integer value) { rating = value; }
    public String getContent() { return content; }
    public void setContent(String value) { content = value; }
    public String getSpecificationSnapshot() { return specificationSnapshot; }
    public void setSpecificationSnapshot(String value) { specificationSnapshot = value; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer value) { status = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime value) { deletedAt = value; }
    public Long getDeletedBy() { return deletedBy; }
    public void setDeletedBy(Long value) { deletedBy = value; }
    public String getDeletionReason() { return deletionReason; }
    public void setDeletionReason(String value) { deletionReason = value; }
}
