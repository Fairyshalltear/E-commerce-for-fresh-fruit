package com.fairyshalltear.freshfruit.order.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.*;

@TableName("shop_order")
public class ShopOrder {
    @TableId(type=IdType.AUTO) public Long id;
    public String orderNo; public Long buyerId; public String idempotencyKey; public String status;
    public BigDecimal subtotal; public BigDecimal shippingFee; public BigDecimal totalAmount; public BigDecimal refundedAmount;
    public String recipientName; public String phone; public String province; public String city; public String district; public String detailAddress;
    public LocalDateTime createdAt; public LocalDateTime paidAt; public LocalDateTime shippedAt; public LocalDateTime completedAt; public LocalDateTime cancelledAt;
}
