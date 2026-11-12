package com.fairyshalltear.freshfruit.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{2,49}$", message = "用户名须以字母开头，长度为 3 至 50 位")
        String username,
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入有效的中国大陆手机号")
        String phone,
        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 72, message = "密码长度须为 8 至 72 位")
        String password,
        @Size(max = 50, message = "昵称不能超过 50 个字符")
        String nickname) {
}
