package com.fairyshalltear.freshfruit.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateDeliveryAddressRequest(
        @NotBlank(message = "收货人不能为空") @Size(max = 50, message = "收货人不能超过 50 个字符")
        String recipientName,
        @NotBlank(message = "联系电话不能为空") @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入有效的中国大陆手机号")
        String phone,
        @NotBlank(message = "省份不能为空") @Size(max = 50, message = "省份不能超过 50 个字符")
        String province,
        @NotBlank(message = "城市不能为空") @Size(max = 50, message = "城市不能超过 50 个字符")
        String city,
        @NotBlank(message = "区县不能为空") @Size(max = 50, message = "区县不能超过 50 个字符")
        String district,
        @NotBlank(message = "详细地址不能为空") @Size(max = 200, message = "详细地址不能超过 200 个字符")
        String detailAddress,
        @NotBlank(message = "地址标签不能为空")
        @Pattern(regexp = "^(HOME|COMPANY)$", message = "地址标签只能为家或公司")
        String addressTag) {
}
