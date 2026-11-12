package com.fairyshalltear.freshfruit.account;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fairyshalltear.freshfruit.account.dto.AccountSummary;
import com.fairyshalltear.freshfruit.account.dto.AdminLoginRequest;
import com.fairyshalltear.freshfruit.account.dto.AuthSession;
import com.fairyshalltear.freshfruit.common.api.ApiResponse;

@RestController
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {
    private final AuthService authService;

    public AdminAuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthSession> login(@Valid @RequestBody AdminLoginRequest request) {
        return ApiResponse.success(authService.loginAdmin(request));
    }

    @GetMapping("/me")
    public ApiResponse<AccountSummary> me(Authentication authentication) {
        Long id = Long.valueOf(((JwtAuthenticationToken) authentication).getToken().getSubject());
        return ApiResponse.success(authService.adminSummary(id));
    }
}
