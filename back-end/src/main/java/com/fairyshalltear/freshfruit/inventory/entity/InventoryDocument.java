package com.fairyshalltear.freshfruit.inventory.entity;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.*;
@TableName("inventory_document")
public class InventoryDocument {
    @TableId(type=IdType.AUTO) public Long id;
    public String documentNo; public String type; public String status;
    public String supplier; public String referenceNo; public String reason;
    public Long version; public Long createdBy; public Long postedBy;
    public LocalDateTime createdAt; public LocalDateTime updatedAt; public LocalDateTime postedAt;
}
