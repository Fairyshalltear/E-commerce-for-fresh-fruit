package com.fairyshalltear.freshfruit.order.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.*;

@TableName("payment_record")
public class PaymentRecord {
    @TableId(type=IdType.AUTO) public Long id;
    public Long orderId; public String idempotencyKey; public String channel; public String result; public BigDecimal amount; public LocalDateTime createdAt;
}
