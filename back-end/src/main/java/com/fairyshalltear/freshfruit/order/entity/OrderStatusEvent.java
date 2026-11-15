package com.fairyshalltear.freshfruit.order.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.*;

@TableName("order_status_event")
public class OrderStatusEvent {
    @TableId(type=IdType.AUTO) public Long id;
    public Long orderId; public String fromStatus; public String toStatus; public String eventType; public String actorType; public Long actorId; public String note; public LocalDateTime createdAt;
}
