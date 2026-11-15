package com.fairyshalltear.freshfruit.order;

public final class OrderTypes {
    private OrderTypes() {}

    public static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    public static final String PENDING_SHIPMENT = "PENDING_SHIPMENT";
    public static final String PENDING_RECEIPT = "PENDING_RECEIPT";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";

    public static String status(String value) {
        return switch (value == null ? "" : value) {
            case "ALL", "" -> null;
            case "PENDING_PAYMENT", "PENDING_SHIPMENT", "PENDING_RECEIPT", "COMPLETED", "CANCELLED" -> value;
            default -> throw new IllegalArgumentException("订单状态无效");
        };
    }
}
