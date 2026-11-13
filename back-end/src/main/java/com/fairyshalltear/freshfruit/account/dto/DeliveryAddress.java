package com.fairyshalltear.freshfruit.account.dto;

public record DeliveryAddress(Long id, String recipientName, String phone, String province, String city,
        String district, String detailAddress, String addressTag, boolean defaultAddress) {
}
