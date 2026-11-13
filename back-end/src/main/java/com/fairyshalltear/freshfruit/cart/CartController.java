package com.fairyshalltear.freshfruit.cart;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fairyshalltear.freshfruit.common.api.ApiResponse;

import static com.fairyshalltear.freshfruit.cart.CartDtos.*;

@RestController
@RequestMapping("/api/v1/buyer/cart")
@Validated
public class CartController {
    private final CartService cart;

    public CartController(CartService cart) {
        this.cart = cart;
    }

    @GetMapping
    public ApiResponse<Cart> get(JwtAuthenticationToken jwt) {
        return ApiResponse.success(cart.get(subject(jwt)));
    }

    @PostMapping
    public ApiResponse<Cart> add(JwtAuthenticationToken jwt, @Valid @RequestBody AddRequest request) {
        return ApiResponse.success(cart.add(subject(jwt), request));
    }

    @PatchMapping("/items/{itemId}/quantity")
    public ApiResponse<Cart> quantity(JwtAuthenticationToken jwt, @PathVariable @Positive Long itemId,
            @Valid @RequestBody QuantityRequest request) {
        return ApiResponse.success(cart.updateQuantity(subject(jwt), itemId, request.quantity()));
    }

    @PatchMapping("/items/{itemId}/selection")
    public ApiResponse<Cart> selection(JwtAuthenticationToken jwt, @PathVariable @Positive Long itemId,
            @Valid @RequestBody SelectionRequest request) {
        return ApiResponse.success(cart.select(subject(jwt), itemId, request.selected()));
    }

    @PutMapping("/selection")
    public ApiResponse<Cart> selectAll(JwtAuthenticationToken jwt, @Valid @RequestBody SelectAllRequest request) {
        return ApiResponse.success(cart.selectAll(subject(jwt), request.selected()));
    }

    @PutMapping("/items/{itemId}/accepted-price")
    public ApiResponse<Cart> acceptPrice(JwtAuthenticationToken jwt, @PathVariable @Positive Long itemId,
            @Valid @RequestBody AcceptPriceRequest request) {
        return ApiResponse.success(cart.acceptPrice(subject(jwt), itemId, request.price()));
    }

    @DeleteMapping("/items/{itemId}")
    public ApiResponse<Cart> remove(JwtAuthenticationToken jwt, @PathVariable @Positive Long itemId) {
        return ApiResponse.success(cart.remove(subject(jwt), itemId));
    }

    @PostMapping("/checkout-validation")
    public ApiResponse<CheckoutValidation> validateCheckout(JwtAuthenticationToken jwt) {
        return ApiResponse.success(cart.validateCheckout(subject(jwt)));
    }

    private Long subject(JwtAuthenticationToken jwt) {
        return Long.valueOf(jwt.getToken().getSubject());
    }
}
