package com.fairyshalltear.freshfruit.review.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.*;

@TableName("product_review_deletion_audit")
public class ProductReviewDeletionAudit {
    @TableId(type=IdType.AUTO) public Long id;
    public Long reviewId; public Long adminId; public String reason; public String reviewSnapshot; public LocalDateTime createdAt;
}
