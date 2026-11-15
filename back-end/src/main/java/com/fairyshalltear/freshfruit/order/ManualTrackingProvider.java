package com.fairyshalltear.freshfruit.order;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;
import com.fairyshalltear.freshfruit.order.entity.ShipmentEvent;
import com.fairyshalltear.freshfruit.order.mapper.ShipmentEventMapper;

@Component
public class ManualTrackingProvider implements TrackingProvider {
    private final ShipmentEventMapper events;
    public ManualTrackingProvider(ShipmentEventMapper events){this.events=events;}
    @Override public List<ShipmentEvent> eventsForOrder(Long orderId){return events.byOrder(orderId);}
    @Override public void appendManualEvent(Long shipmentId,String description,LocalDateTime occurredAt){
        ShipmentEvent event=new ShipmentEvent();event.shipmentId=shipmentId;event.description=description;event.occurredAt=occurredAt;event.createdAt=LocalDateTime.now();events.insert(event);
    }
}
