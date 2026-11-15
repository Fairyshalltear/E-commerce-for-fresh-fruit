package com.fairyshalltear.freshfruit.aftersales.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.*;

@TableName("refund_record")
public class RefundRecord {
    @TableId(type=IdType.AUTO) public Long id;
    public Long requestId; public Long orderId; public BigDecimal amount; public String result; public LocalDateTime createdAt;
}
