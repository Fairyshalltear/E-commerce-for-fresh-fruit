package com.fairyshalltear.freshfruit.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public final class OrderDtos {
    private OrderDtos() {}

    public record PurchaseItem(@NotNull @Positive Long skuId, @NotNull @Min(1) @Max(999) Integer quantity) {}
    public record CheckoutRequest(@NotNull @Positive Long addressId, @NotBlank @Size(max=80) String requestKey,
            @Size(max=50) List<@NotNull @Valid PurchaseItem> items) {}
    public record CheckoutLine(Long skuId, String productName, String specification, String imageUrl,
            int quantity, @JsonSerialize(using=ToStringSerializer.class) BigDecimal unitPrice,
            @JsonSerialize(using=ToStringSerializer.class) BigDecimal lineAmount, int availableStock, boolean purchasable,
            String unavailableReason, boolean priceChanged) {}
    public record CheckoutPreview(List<CheckoutLine> items, Long addressId, String recipientName, String phone,
            String formattedAddress, @JsonSerialize(using=ToStringSerializer.class) BigDecimal subtotal,
            @JsonSerialize(using=ToStringSerializer.class) BigDecimal shippingFee,
            @JsonSerialize(using=ToStringSerializer.class) BigDecimal total,
            boolean valid, String message) {}
    public record OrderItemView(Long id, Long productId, Long skuId, String productName, String specification,
            String imageUrl, int quantity, int shippedQuantity, int refundedQuantity,
            @JsonSerialize(using=ToStringSerializer.class) BigDecimal unitPrice,
            @JsonSerialize(using=ToStringSerializer.class) BigDecimal lineAmount,
            boolean reviewed) {}
    public record StatusEventView(String fromStatus, String toStatus, String eventType, String actorType,
            String note, LocalDateTime createdAt) {}
    public record TrackingEventView(String description, LocalDateTime occurredAt) {}
    public record OrderSummary(Long id, String orderNo, String status, int itemCount,
            @JsonSerialize(using=ToStringSerializer.class) BigDecimal totalAmount,
            LocalDateTime createdAt) {}
    public record OrderDetail(Long id, String orderNo, String status, String recipientName, String phone,
            String province, String city, String district, String detailAddress,
            @JsonSerialize(using=ToStringSerializer.class) BigDecimal subtotal,
            @JsonSerialize(using=ToStringSerializer.class) BigDecimal shippingFee,
            @JsonSerialize(using=ToStringSerializer.class) BigDecimal totalAmount,
            LocalDateTime createdAt, LocalDateTime paidAt, LocalDateTime shippedAt, LocalDateTime completedAt,
            String carrier, String trackingNumber, List<OrderItemView> items,
            List<StatusEventView> statusEvents, List<TrackingEventView> trackingEvents) {}
    public record Page<T>(List<T> items, long total, int page, int size) {}
    public record OrderQuery(@Pattern(regexp="ALL|PENDING_PAYMENT|PENDING_SHIPMENT|PENDING_RECEIPT|COMPLETED|CANCELLED") String status,
            @Min(1) Integer page, @Min(1) @Max(100) Integer size,
            LocalDateTime from, LocalDateTime to, @Size(max=100) String keyword) {
        public OrderQuery { if (page == null) page=1; if (size == null) size=12; if (status == null) status="ALL"; }
    }
    public record PaymentRequest(@Pattern(regexp="ALIPAY|WECHAT") String channel,
            @Pattern(regexp="SUCCESS|FAILED|CANCELLED") String result, @NotBlank @Size(max=80) String requestKey) {}
    public record DispatchRequest(@NotBlank @Size(max=80) String carrier,
            @NotBlank @Size(max=100) String trackingNumber) {}
    public record TrackingEventRequest(@NotBlank @Size(max=500) String description,
            @NotNull LocalDateTime occurredAt) {}
}
