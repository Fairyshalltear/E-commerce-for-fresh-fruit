package com.fairyshalltear.freshfruit.account;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.fairyshalltear.freshfruit.account.dto.BuyerProfile;
import com.fairyshalltear.freshfruit.account.dto.ChangeBuyerPasswordRequest;
import com.fairyshalltear.freshfruit.account.dto.UpdateBuyerProfileRequest;
import com.fairyshalltear.freshfruit.common.api.ApiResponse;

@RestController
@RequestMapping("/api/v1/buyer/profile")
public class BuyerProfileController {
    private final BuyerProfileService profiles;

    public BuyerProfileController(BuyerProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public ApiResponse<BuyerProfile> getProfile(Authentication authentication) {
        return ApiResponse.success(profiles.getProfile(subjectId(authentication)));
    }

    @PatchMapping
    public ApiResponse<BuyerProfile> updateProfile(Authentication authentication,
            @Valid @RequestBody UpdateBuyerProfileRequest request) {
        return ApiResponse.success(profiles.updateProfile(subjectId(authentication), request));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(Authentication authentication,
            @Valid @RequestBody ChangeBuyerPasswordRequest request) {
        profiles.changePassword(subjectId(authentication), request);
        return ApiResponse.success(null);
    }

    private Long subjectId(Authentication authentication) {
        return Long.valueOf(((JwtAuthenticationToken) authentication).getToken().getSubject());
    }
}
