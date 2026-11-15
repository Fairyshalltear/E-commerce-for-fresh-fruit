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
@RequestMapping("/api/v1/buyer/orders")
@Validated
public class OrderController {
    private final OrderService orders;
    public OrderController(OrderService orders) { this.orders=orders; }

    @PostMapping("/checkout-preview")
    public ApiResponse<CheckoutPreview> preview(Authentication auth,@Valid @RequestBody CheckoutRequest request) {
        return ApiResponse.success(orders.preview(subject(auth),request));
    }
    @PostMapping
    public ApiResponse<OrderDetail> submit(Authentication auth,@Valid @RequestBody CheckoutRequest request) {
        return ApiResponse.success(orders.submit(subject(auth),request));
    }
    @GetMapping
    public ApiResponse<Page<OrderSummary>> list(Authentication auth,@Valid @ModelAttribute OrderQuery query) {
        return ApiResponse.success(orders.buyerOrders(subject(auth),query));
    }
    @GetMapping("/{orderId}")
    public ApiResponse<OrderDetail> detail(Authentication auth,@PathVariable @Positive Long orderId) {
        return ApiResponse.success(orders.buyerDetail(subject(auth),orderId));
    }
    @PostMapping("/{orderId}/payment")
    public ApiResponse<OrderDetail> pay(Authentication auth,@PathVariable @Positive Long orderId,@Valid @RequestBody PaymentRequest request) {
        return ApiResponse.success(orders.pay(subject(auth),orderId,request));
    }
    @PostMapping("/{orderId}/cancel")
    public ApiResponse<OrderDetail> cancel(Authentication auth,@PathVariable @Positive Long orderId) {
        return ApiResponse.success(orders.cancel(subject(auth),orderId));
    }
    @PostMapping("/{orderId}/confirm-receipt")
    public ApiResponse<OrderDetail> confirmReceipt(Authentication auth,@PathVariable @Positive Long orderId) {
        return ApiResponse.success(orders.confirmReceipt(subject(auth),orderId));
    }
    private Long subject(Authentication auth) {return Long.valueOf(((JwtAuthenticationToken)auth).getToken().getSubject());}
}
