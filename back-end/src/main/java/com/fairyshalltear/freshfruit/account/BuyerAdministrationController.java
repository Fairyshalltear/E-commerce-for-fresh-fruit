package com.fairyshalltear.freshfruit.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fairyshalltear.freshfruit.common.api.ApiResponse;
import com.fairyshalltear.freshfruit.account.dto.BuyerAdminDtos.*;

@RestController
@RequestMapping("/api/v1/admin/buyers")
@Validated
public class BuyerAdministrationController {
    private final BuyerAdministrationService service;

    public BuyerAdministrationController(BuyerAdministrationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<Page<BuyerSummary>> list(
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) @Min(0) @Max(1) Integer status,
            @RequestParam(required = false) @Min(1) Integer page,
            @RequestParam(required = false) @Min(1) @Max(100) Integer size) {
        return ApiResponse.success(service.list(new Query(keyword, status, page, size)));
    }

    @GetMapping("/{buyerId}")
    public ApiResponse<BuyerDetail> get(@PathVariable @Positive Long buyerId) {
        return ApiResponse.success(service.get(buyerId));
    }

    @PatchMapping("/{buyerId}/status")
    public ApiResponse<BuyerDetail> setStatus(JwtAuthenticationToken jwt, @PathVariable @Positive Long buyerId,
            @Valid @RequestBody StatusRequest request) {
        return ApiResponse.success(service.setStatus(adminId(jwt), buyerId, request.status()));
    }

    @PostMapping("/{buyerId}/password-reset")
    public ApiResponse<Void> resetPassword(JwtAuthenticationToken jwt, @PathVariable @Positive Long buyerId,
            @Valid @RequestBody PasswordResetRequest request) {
        service.resetPassword(adminId(jwt), buyerId, request.temporaryPassword());
        return ApiResponse.success(null);
    }

    private Long adminId(JwtAuthenticationToken jwt) {
        return Long.valueOf(jwt.getToken().getSubject());
    }
}
