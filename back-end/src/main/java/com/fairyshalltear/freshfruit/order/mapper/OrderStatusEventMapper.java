package com.fairyshalltear.freshfruit.order.mapper;

import java.util.List;
import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fairyshalltear.freshfruit.order.entity.OrderStatusEvent;

@Mapper
public interface OrderStatusEventMapper extends BaseMapper<OrderStatusEvent> {
    @Select("SELECT * FROM order_status_event WHERE order_id=#{orderId} ORDER BY created_at,id") List<OrderStatusEvent> byOrder(@Param("orderId") Long orderId);
}
