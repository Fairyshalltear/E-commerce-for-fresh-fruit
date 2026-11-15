package com.fairyshalltear.freshfruit.inventory;
import static com.fairyshalltear.freshfruit.inventory.InventoryDtos.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fairyshalltear.freshfruit.common.api.ApiResponse;
@RestController
@RequestMapping("/api/v1/admin/inventory")
@Validated
public class AdminInventoryController {
    private final InventoryManagementService inventory;
    public AdminInventoryController(InventoryManagementService inventory){this.inventory=inventory;}
    @GetMapping("/stocks") public ApiResponse<Page<Stock>> stocks(@Valid @ModelAttribute StockQuery q){return ApiResponse.success(inventory.stockList(q,false));}
    @GetMapping("/summary") public ApiResponse<Summary> summary(@Valid @ModelAttribute StockQuery q){return ApiResponse.success(inventory.summary(q));}
    @GetMapping("/sku-options") public ApiResponse<Page<Stock>> options(@Valid @ModelAttribute StockQuery q){return ApiResponse.success(inventory.stockList(q,true));}
    @GetMapping("/alerts/count") public ApiResponse<Long> count(){return ApiResponse.success(inventory.alertCount());}
    @PatchMapping("/stocks/{id}/threshold") public ApiResponse<Stock> threshold(Authentication a,@PathVariable @Positive Long id,@Valid @RequestBody Threshold r){return ApiResponse.success(inventory.threshold(subject(a),id,r));}
    @GetMapping("/movements") public ApiResponse<Page<Movement>> movements(@Valid @ModelAttribute MovementQuery q){return ApiResponse.success(inventory.movementList(q));}
    @GetMapping("/documents") public ApiResponse<Page<DocumentView>> list(@Valid @ModelAttribute DocumentQuery q){return ApiResponse.success(inventory.documentList(q));}
    @GetMapping("/documents/{id}") public ApiResponse<DocumentView> detail(@PathVariable @Positive Long id){return ApiResponse.success(inventory.detail(id));}
    @PostMapping("/documents") public ApiResponse<DocumentView> create(Authentication a,@Valid @RequestBody Draft r){return ApiResponse.success(inventory.save(subject(a),null,r));}
    @PutMapping("/documents/{id}") public ApiResponse<DocumentView> update(Authentication a,@PathVariable @Positive Long id,@Valid @RequestBody Draft r){return ApiResponse.success(inventory.save(subject(a),id,r));}
    @DeleteMapping("/documents/{id}") public ApiResponse<Void> delete(@PathVariable @Positive Long id,@RequestParam @Min(0) Long version){inventory.delete(id,version);return ApiResponse.success(null);}
    @PostMapping("/documents/{id}/post") public ApiResponse<DocumentView> post(Authentication a,@PathVariable @Positive Long id,@Valid @RequestBody Version r){return ApiResponse.success(inventory.post(subject(a),id,r.version()));}
    @PostMapping("/documents/{id}/refresh-snapshot") public ApiResponse<DocumentView> refresh(@PathVariable @Positive Long id,@Valid @RequestBody Version r){return ApiResponse.success(inventory.refresh(id,r.version()));}
    private static Long subject(Authentication a){return Long.valueOf(((JwtAuthenticationToken)a).getToken().getSubject());}
}
