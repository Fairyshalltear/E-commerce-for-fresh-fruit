package com.fairyshalltear.freshfruit.order.mapper;

import java.util.List;
import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fairyshalltear.freshfruit.order.entity.ShipmentEvent;

@Mapper
public interface ShipmentEventMapper extends BaseMapper<ShipmentEvent> {
    @Select("SELECT e.* FROM shipment_event e JOIN shipment s ON s.id=e.shipment_id WHERE s.order_id=#{orderId} ORDER BY e.occurred_at,e.id") List<ShipmentEvent> byOrder(@Param("orderId") Long orderId);
}
