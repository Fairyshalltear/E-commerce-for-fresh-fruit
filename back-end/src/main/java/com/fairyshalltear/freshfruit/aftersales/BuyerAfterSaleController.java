package com.fairyshalltear.freshfruit.aftersales;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import com.fairyshalltear.freshfruit.common.api.ApiResponse;
import static com.fairyshalltear.freshfruit.aftersales.AfterSaleDtos.*;

@RestController
@RequestMapping("/api/v1/buyer")
@Validated
public class BuyerAfterSaleController {
    private final AfterSaleService afterSales;
    public BuyerAfterSaleController(AfterSaleService afterSales){this.afterSales=afterSales;}
    @PostMapping("/orders/{orderId}/after-sales") public ApiResponse<RequestView> submit(Authentication auth,@PathVariable @Positive Long orderId,@Valid @RequestBody CreateRequest request){return ApiResponse.success(afterSales.submit(subject(auth),orderId,request));}
    @GetMapping("/after-sales") public ApiResponse<Page<RequestView>> list(Authentication auth){return ApiResponse.success(afterSales.buyerList(subject(auth)));}
    @GetMapping("/after-sales/{id}") public ApiResponse<RequestView> detail(Authentication auth,@PathVariable @Positive Long id){return ApiResponse.success(afterSales.buyerDetail(subject(auth),id));}
    @PostMapping("/after-sales/{id}/return-shipment") public ApiResponse<RequestView> returnShipment(Authentication auth,@PathVariable @Positive Long id,@Valid @RequestBody ReturnShipmentRequest request){return ApiResponse.success(afterSales.submitReturnShipment(subject(auth),id,request));}
    private Long subject(Authentication auth){return Long.valueOf(((JwtAuthenticationToken)auth).getToken().getSubject());}
}
