package com.fairyshalltear.freshfruit.cart;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public final class CartDtos {
    private CartDtos() {}

    public record AddRequest(@NotNull @Positive Long skuId, @NotNull @Positive Integer quantity) {}
    public record QuantityRequest(@NotNull @Positive Integer quantity) {}
    public record SelectionRequest(@NotNull Boolean selected) {}
    public record SelectAllRequest(@NotNull Boolean selected) {}
    public record AcceptPriceRequest(@NotNull @DecimalMin("0.01") BigDecimal price) {}

    public record Item(Long id, Long skuId, String productName, String specification, String imageUrl,
            int quantity, boolean selected,
            @JsonSerialize(using = ToStringSerializer.class) BigDecimal currentUnitPrice,
            @JsonSerialize(using = ToStringSerializer.class) BigDecimal acceptedUnitPrice,
            int availableStock, boolean purchasable, boolean priceChanged, String unavailableReason,
            @JsonSerialize(using = ToStringSerializer.class) BigDecimal lineAmount) {}

    public record Cart(List<Item> items, long selectedCount,
            @JsonSerialize(using = ToStringSerializer.class) BigDecimal selectedSubtotal,
            @JsonSerialize(using = ToStringSerializer.class) BigDecimal shippingFee,
            @JsonSerialize(using = ToStringSerializer.class) BigDecimal total) {}

    public record CheckoutValidation(boolean valid, Cart cart, List<Long> attentionItemIds,
            String message) {}
}
