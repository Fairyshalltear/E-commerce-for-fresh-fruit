package com.fairyshalltear.freshfruit.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BuyerLoginRequest(
        @NotBlank(message = "用户名或手机号不能为空") @Size(max = 50) String usernameOrPhone,
        @NotBlank(message = "密码不能为空") @Size(max = 72) String password) {
}
