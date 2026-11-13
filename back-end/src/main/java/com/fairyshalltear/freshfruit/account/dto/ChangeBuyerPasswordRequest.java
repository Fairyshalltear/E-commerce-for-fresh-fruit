package com.fairyshalltear.freshfruit.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeBuyerPasswordRequest(
        @NotBlank(message = "当前密码不能为空")
        String currentPassword,
        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 72, message = "密码长度须为 8 至 72 位")
        String newPassword) {
}
