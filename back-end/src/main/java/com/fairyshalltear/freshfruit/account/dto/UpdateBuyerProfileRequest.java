package com.fairyshalltear.freshfruit.account.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateBuyerProfileRequest(
        @Size(max = 50, message = "昵称不能超过 50 个字符")
        String nickname,
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入有效的中国大陆手机号")
        String phone,
        String username) {
}
