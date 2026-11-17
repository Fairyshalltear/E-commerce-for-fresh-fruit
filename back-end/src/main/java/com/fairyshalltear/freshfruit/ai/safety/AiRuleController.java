package com.fairyshalltear.freshfruit.ai.safety;

import java.util.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.fairyshalltear.freshfruit.ai.AiDtos.*;
import com.fairyshalltear.freshfruit.common.api.ApiResponse;

@org.springframework.validation.annotation.Validated
@RestController
@RequestMapping("/api/v1/admin/ai/rules")
public class AiRuleController {
    private final AiRuleService service;
    public AiRuleController(AiRuleService service){this.service=service;}
    @GetMapping public ApiResponse<Page<Rule>> list(@RequestParam(defaultValue="1") @jakarta.validation.constraints.Min(1) int page,@RequestParam(defaultValue="20") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) int size,@RequestParam(required=false) String scope){return ApiResponse.success(service.page(page,size,scope));}
    @PostMapping public ApiResponse<Rule> create(JwtAuthenticationToken jwt,@Valid @RequestBody RuleInput input){return ApiResponse.success(service.save(subject(jwt),null,input));}
    @PutMapping("/{id}") public ApiResponse<Rule> save(JwtAuthenticationToken jwt,@PathVariable long id,@Valid @RequestBody RuleInput input){return ApiResponse.success(service.save(subject(jwt),id,input));}
    @PatchMapping("/{id}/enabled") public ApiResponse<Rule> enabled(JwtAuthenticationToken jwt,@PathVariable long id,@Valid @RequestBody EnabledInput input){return ApiResponse.success(service.enabled(subject(jwt),id,input));}
    @PostMapping("/test") public ApiResponse<Map<String,Object>> test(@Valid @RequestBody SearchInput input,@RequestParam(defaultValue="INPUT") String scope){
        if(!Set.of("INPUT","OUTPUT").contains(scope))throw com.fairyshalltear.freshfruit.ai.AiErrors.invalid("未知匹配阶段");
        var hit=AiRuleService.match(service.snapshot(),scope,input.question());
        return ApiResponse.success(Map.of("blocked",hit.isPresent(),"rules",hit.map(List::of).orElse(List.of())));
    }
    private long subject(JwtAuthenticationToken jwt){return Long.parseLong(jwt.getToken().getSubject());}
}
