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
@RequestMapping("/api/v1/admin/after-sales")
@Validated
public class AdminAfterSaleController {
    private final AfterSaleService afterSales;
    public AdminAfterSaleController(AfterSaleService afterSales){this.afterSales=afterSales;}
    @GetMapping public ApiResponse<Page<RequestView>> list(@Valid @ModelAttribute Query query){return ApiResponse.success(afterSales.adminList(query));}
    @GetMapping("/{id}") public ApiResponse<RequestView> detail(@PathVariable @Positive Long id){return ApiResponse.success(afterSales.adminDetail(id));}
    @PostMapping("/{id}/review") public ApiResponse<RequestView> review(Authentication auth,@PathVariable @Positive Long id,@Valid @RequestBody ReviewRequest request){return ApiResponse.success(afterSales.review(subject(auth),id,request));}
    @PostMapping("/{id}/receive") public ApiResponse<RequestView> receive(Authentication auth,@PathVariable @Positive Long id,@Valid @RequestBody ReceiveRequest request){return ApiResponse.success(afterSales.receive(subject(auth),id,request));}
    private Long subject(Authentication auth){return Long.valueOf(((JwtAuthenticationToken)auth).getToken().getSubject());}
}
