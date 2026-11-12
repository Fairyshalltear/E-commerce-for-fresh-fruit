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
import com.fairyshalltear.freshfruit.account.dto.AuthSession;
import com.fairyshalltear.freshfruit.account.dto.BuyerLoginRequest;
import com.fairyshalltear.freshfruit.account.dto.RegisterRequest;
import com.fairyshalltear.freshfruit.common.api.ApiResponse;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthSession> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthSession> login(@Valid @RequestBody BuyerLoginRequest request) {
        return ApiResponse.success(authService.loginBuyer(request));
    }

    @GetMapping("/buyer/me")
    public ApiResponse<AccountSummary> buyerMe(Authentication authentication) {
        return ApiResponse.success(authService.buyerSummary(subjectId(authentication)));
    }

    private Long subjectId(Authentication authentication) {
        return Long.valueOf(((JwtAuthenticationToken) authentication).getToken().getSubject());
    }
}
