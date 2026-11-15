package com.fairyshalltear.freshfruit.order.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.*;

@TableName("shipment")
public class Shipment {
    @TableId(type=IdType.AUTO) public Long id;
    public Long orderId; public String carrier; public String trackingNumber; public LocalDateTime createdAt;
}
