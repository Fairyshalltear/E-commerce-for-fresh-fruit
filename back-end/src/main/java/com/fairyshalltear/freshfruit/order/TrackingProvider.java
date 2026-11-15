package com.fairyshalltear.freshfruit.order;

import java.time.LocalDateTime;
import java.util.List;
import com.fairyshalltear.freshfruit.order.entity.ShipmentEvent;

public interface TrackingProvider {
    List<ShipmentEvent> eventsForOrder(Long orderId);
    void appendManualEvent(Long shipmentId,String description,LocalDateTime occurredAt);
}
