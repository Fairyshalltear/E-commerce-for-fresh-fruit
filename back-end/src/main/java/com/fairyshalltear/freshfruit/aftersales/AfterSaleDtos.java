package com.fairyshalltear.freshfruit.aftersales;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public final class AfterSaleDtos {
    private AfterSaleDtos() {}
    public record RequestedItem(@NotNull @Positive Long orderItemId,@NotNull @Min(1) @Max(999) Integer quantity) {}
    public record CreateRequest(@NotBlank @Pattern(regexp="REFUND|RETURN") String type,@NotBlank @Size(max=100) String reason,
            @Size(max=1000) String description,@NotEmpty @Size(max=50) List<@NotNull @Valid RequestedItem> items) {}
    public record ReviewRequest(@NotNull Boolean approved,@NotBlank @Size(max=500) String reason) {}
    public record ReturnShipmentRequest(@NotBlank @Size(max=80) String carrier,@NotBlank @Size(max=100) String trackingNumber) {}
    public record ReceivedItem(@NotNull @Positive Long orderItemId,@NotNull @Min(0) @Max(999) Integer refundQuantity,
            @NotNull @Min(0) @Max(999) Integer restockQuantity) {}
    public record ReceiveRequest(@NotEmpty @Size(max=50) List<@NotNull @Valid ReceivedItem> items) {}
    public record Query(@Pattern(regexp="PENDING_REVIEW|WAITING_RETURN|WAITING_RECEIPT|COMPLETED|REJECTED") String status,
            @Pattern(regexp="REFUND|RETURN") String type,@Min(1) Integer page,@Min(1) @Max(100) Integer size) {
        public Query {if(page==null)page=1;if(size==null)size=12;}
    }
    public record ItemView(Long orderItemId,String productName,String specification,int requestedQuantity,
            int refundQuantity,int restockQuantity,@JsonSerialize(using=ToStringSerializer.class) BigDecimal unitPrice) {}
    public record RequestView(Long id,String requestNo,Long orderId,String orderNo,String type,String status,
            String reason,String description,@JsonSerialize(using=ToStringSerializer.class) BigDecimal requestedAmount,
            String reviewReason,String returnCarrier,String returnTrackingNumber,LocalDateTime createdAt,
            LocalDateTime reviewedAt,LocalDateTime receivedAt,List<ItemView> items) {}
    public record Page<T>(List<T> items,long total,int page,int size) {}
}
