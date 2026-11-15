package com.fairyshalltear.freshfruit.order;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import com.fairyshalltear.freshfruit.common.api.ApiResponse;
import static com.fairyshalltear.freshfruit.order.OrderDtos.*;

@RestController
@RequestMapping("/api/v1/admin/orders")
@Validated
public class AdminOrderController {
    private final OrderService orders;
    public AdminOrderController(OrderService orders){this.orders=orders;}
    @GetMapping public ApiResponse<Page<OrderSummary>> list(@Valid @ModelAttribute OrderQuery query){return ApiResponse.success(orders.adminOrders(query));}
    @GetMapping("/{orderId}") public ApiResponse<OrderDetail> detail(@PathVariable @Positive Long orderId){return ApiResponse.success(orders.adminDetail(orderId));}
    @PostMapping("/{orderId}/dispatch") public ApiResponse<OrderDetail> dispatch(Authentication auth,@PathVariable @Positive Long orderId,@Valid @RequestBody DispatchRequest request){return ApiResponse.success(orders.dispatch(subject(auth),orderId,request.carrier(),request.trackingNumber()));}
    @PostMapping("/{orderId}/tracking-events") public ApiResponse<OrderDetail> tracking(Authentication auth,@PathVariable @Positive Long orderId,@Valid @RequestBody TrackingEventRequest request){return ApiResponse.success(orders.addTrackingEvent(subject(auth),orderId,request.description(),request.occurredAt()));}
    private Long subject(Authentication auth){return Long.valueOf(((JwtAuthenticationToken)auth).getToken().getSubject());}
}
