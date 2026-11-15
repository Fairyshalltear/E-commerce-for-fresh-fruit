package com.fairyshalltear.freshfruit.inventory.entity;
import com.baomidou.mybatisplus.annotation.*;
@TableName("inventory_document_item")
public class InventoryDocumentItem {
    @TableId(type=IdType.AUTO) public Long id;
    public Long documentId; public Long skuId;
    public String productNameSnapshot; public String specificationSnapshot;
    public Integer quantity; public Integer countedQuantity;
    public Integer beforeOnHand; public Integer beforeReserved; public Long inventoryVersion;
    public Integer afterOnHand; public Integer quantityDelta;
}
