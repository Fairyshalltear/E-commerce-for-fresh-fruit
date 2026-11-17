package com.fairyshalltear.freshfruit.ai.admin;

import java.time.LocalDate;
import java.util.Map;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.fairyshalltear.freshfruit.ai.AiDtos.Page;
import com.fairyshalltear.freshfruit.common.api.ApiResponse;

@RestController
@RequestMapping("/api/v1/admin/ai")
@Validated
public class AiAdministrationController {
    private final AiStatisticsService stats;private final AiRecordService records;
    public AiAdministrationController(AiStatisticsService stats,AiRecordService records){this.stats=stats;this.records=records;}
    @GetMapping("/statistics") public ApiResponse<Map<String,Object>> statistics(@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to){return ApiResponse.success(stats.statistics(from,to));}
    @GetMapping("/conversations") public ApiResponse<Page<Map<String,Object>>> list(@RequestParam(defaultValue="1") @Min(1) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size,@RequestParam(required=false) String ownerKind,@RequestParam(required=false) String scene,@RequestParam(required=false) String status,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,@RequestParam(required=false) @Positive Long buyerId,@RequestParam(required=false) @Size(max=100) String keyword,@RequestParam(required=false) Boolean helpful){return ApiResponse.success(records.list(page,size,ownerKind,scene,status,from,to,buyerId,keyword,helpful));}
    @GetMapping("/conversations/{id}") public ApiResponse<Map<String,Object>> detail(JwtAuthenticationToken jwt,@PathVariable @Positive long id){return ApiResponse.success(records.detail(Long.parseLong(jwt.getToken().getSubject()),id));}
}
