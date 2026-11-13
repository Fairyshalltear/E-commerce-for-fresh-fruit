package com.fairyshalltear.freshfruit.account;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.fairyshalltear.freshfruit.account.dto.CreateDeliveryAddressRequest;
import com.fairyshalltear.freshfruit.account.dto.DeliveryAddress;
import com.fairyshalltear.freshfruit.account.dto.UpdateDeliveryAddressRequest;
import com.fairyshalltear.freshfruit.common.api.ApiResponse;

@RestController
@RequestMapping("/api/v1/buyer/addresses")
public class DeliveryAddressController {
    private final DeliveryAddressService addresses;

    public DeliveryAddressController(DeliveryAddressService addresses) {
        this.addresses = addresses;
    }

    @GetMapping
    public ApiResponse<List<DeliveryAddress>> list(Authentication authentication) {
        return ApiResponse.success(addresses.list(subjectId(authentication)));
    }

    @GetMapping("/{addressId}")
    public ApiResponse<DeliveryAddress> get(Authentication authentication, @PathVariable Long addressId) {
        return ApiResponse.success(addresses.get(subjectId(authentication), addressId));
    }

    @PostMapping
    public ApiResponse<DeliveryAddress> create(Authentication authentication,
            @Valid @RequestBody CreateDeliveryAddressRequest request) {
        return ApiResponse.success(addresses.create(subjectId(authentication), request));
    }

    @PutMapping("/{addressId}")
    public ApiResponse<DeliveryAddress> update(Authentication authentication, @PathVariable Long addressId,
            @Valid @RequestBody UpdateDeliveryAddressRequest request) {
        return ApiResponse.success(addresses.update(subjectId(authentication), addressId, request));
    }

    @DeleteMapping("/{addressId}")
    public ApiResponse<Void> delete(Authentication authentication, @PathVariable Long addressId) {
        addresses.delete(subjectId(authentication), addressId);
        return ApiResponse.success(null);
    }

    @PutMapping("/{addressId}/default")
    public ApiResponse<DeliveryAddress> setDefault(Authentication authentication, @PathVariable Long addressId) {
        return ApiResponse.success(addresses.setDefault(subjectId(authentication), addressId));
    }

    private Long subjectId(Authentication authentication) {
        return Long.valueOf(((JwtAuthenticationToken) authentication).getToken().getSubject());
    }
}
