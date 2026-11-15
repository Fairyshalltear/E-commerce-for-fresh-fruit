package com.fairyshalltear.freshfruit.aftersales.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.*;

@TableName("after_sale_request")
public class AfterSaleRequest {
    @TableId(type=IdType.AUTO) public Long id;
    public String requestNo; public Long orderId; public Long buyerId; public String type; public String status;
    public String reason; public String description; public BigDecimal requestedAmount; public String reviewReason;
    public Long reviewedBy; public LocalDateTime reviewedAt; public String returnCarrier; public String returnTrackingNumber;
    public LocalDateTime receivedAt; public LocalDateTime createdAt; public LocalDateTime updatedAt;
}
