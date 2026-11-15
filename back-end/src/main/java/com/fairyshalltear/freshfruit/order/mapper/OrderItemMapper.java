package com.fairyshalltear.freshfruit.order.mapper;

import java.util.List;
import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fairyshalltear.freshfruit.order.entity.OrderItem;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
    @Select("SELECT * FROM order_item WHERE order_id=#{orderId} ORDER BY sku_id,id FOR UPDATE") List<OrderItem> lockByOrder(@Param("orderId") Long orderId);
    @Select("SELECT * FROM order_item WHERE id=#{id} AND order_id=#{orderId} FOR UPDATE") OrderItem lockOwned(@Param("id") Long id,@Param("orderId") Long orderId);
    @Select("SELECT * FROM order_item WHERE id=#{id} FOR UPDATE") OrderItem lockById(@Param("id") Long id);
}
