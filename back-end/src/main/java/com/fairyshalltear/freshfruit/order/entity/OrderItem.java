package com.fairyshalltear.freshfruit.order.entity;

import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.*;

@TableName("order_item")
public class OrderItem {
    @TableId(type=IdType.AUTO) public Long id;
    public Long orderId; public Long productId; public Long skuId; public String productNameSnapshot; public String specificationSnapshot;
    public String imageSnapshot; public BigDecimal unitPrice; public Integer quantity; public Integer shippedQuantity;
    public Integer refundPendingQuantity; public Integer refundedQuantity; public Integer restockedQuantity; public BigDecimal lineAmount;
}
