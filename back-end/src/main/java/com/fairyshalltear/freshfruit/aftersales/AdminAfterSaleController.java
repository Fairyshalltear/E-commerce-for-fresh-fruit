package com.fairyshalltear.freshfruit.aftersales;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import com.fairyshalltear.freshfruit.common.api.ApiResponse;
import com.fairyshalltear.freshfruit.security.AdminPermissionService;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.fairyshalltear.freshfruit.aftersales.AfterSaleDtos.*;

@RestController
@RequestMapping("/api/v1/admin/after-sales")
@Validated
public class AdminAfterSaleController {
    private final AfterSaleService afterSales;
    private final AdminPermissionService permissions;
    private final JdbcTemplate jdbc;
    public AdminAfterSaleController(AfterSaleService afterSales, AdminPermissionService permissions, JdbcTemplate jdbc){this.afterSales=afterSales;this.permissions=permissions;this.jdbc=jdbc;}
    @GetMapping public ApiResponse<Page<RequestView>> list(@Valid @ModelAttribute Query query){return ApiResponse.success(afterSales.adminList(query));}
    @GetMapping("/{id}") public ApiResponse<RequestView> detail(@PathVariable @Positive Long id){return ApiResponse.success(afterSales.adminDetail(id));}
    @PostMapping("/{id}/review") public ApiResponse<RequestView> review(Authentication auth,@PathVariable @Positive Long id,@Valid @RequestBody ReviewRequest request){Long actor=subject(auth);if(request.approved()&&"REFUND".equals(jdbc.query("SELECT type FROM after_sale_request WHERE id=?",rs->rs.next()?rs.getString(1):null,id)))permissions.requireOperation(actor,"AFTER_SALES","REFUND_EXECUTE");return ApiResponse.success(afterSales.review(actor,id,request));}
    @PostMapping("/{id}/receive") public ApiResponse<RequestView> receive(Authentication auth,@PathVariable @Positive Long id,@Valid @RequestBody ReceiveRequest request){Long actor=subject(auth);if(request.items().stream().anyMatch(item->item.refundQuantity()>0))permissions.requireOperation(actor,"AFTER_SALES","REFUND_EXECUTE");return ApiResponse.success(afterSales.receive(actor,id,request));}
    private Long subject(Authentication auth){return Long.valueOf(((JwtAuthenticationToken)auth).getToken().getSubject());}
}
