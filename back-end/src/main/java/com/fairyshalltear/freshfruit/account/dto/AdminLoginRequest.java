package com.fairyshalltear.freshfruit.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminLoginRequest(
        @NotBlank(message = "管理员用户名不能为空") @Size(max = 50) String username,
        @NotBlank(message = "密码不能为空") @Size(max = 72) String password) {
}
