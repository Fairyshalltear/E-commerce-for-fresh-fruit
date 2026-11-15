package com.fairyshalltear.freshfruit.aftersales.entity;

import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.*;

@TableName("after_sale_item")
public class AfterSaleItem {
    @TableId(type=IdType.AUTO) public Long id;
    public Long requestId; public Long orderItemId; public Integer quantity; public Integer refundQuantity; public Integer restockQuantity; public BigDecimal unitPrice;
}
