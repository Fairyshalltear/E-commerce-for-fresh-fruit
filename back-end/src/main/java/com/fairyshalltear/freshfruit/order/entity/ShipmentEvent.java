package com.fairyshalltear.freshfruit.order.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.*;

@TableName("shipment_event")
public class ShipmentEvent {
    @TableId(type=IdType.AUTO) public Long id;
    public Long shipmentId; public String description; public LocalDateTime occurredAt; public LocalDateTime createdAt;
}
