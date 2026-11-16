package com.fairyshalltear.freshfruit.audit;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fairyshalltear.freshfruit.common.api.ApiResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import static com.fairyshalltear.freshfruit.audit.AuditDtos.*;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@Validated
public class AdminAuditLogController {
    private final AuditQueryService queries;

    public AdminAuditLogController(AuditQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    public ApiResponse<Page> list(@Valid @ModelAttribute Query query) {
        return ApiResponse.success(queries.list(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<LogView> detail(@PathVariable @Positive Long id) {
        return ApiResponse.success(queries.detail(id));
    }
}
