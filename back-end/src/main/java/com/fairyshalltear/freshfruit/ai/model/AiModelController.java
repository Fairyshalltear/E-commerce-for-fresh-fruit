package com.fairyshalltear.freshfruit.ai.model;

import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.fairyshalltear.freshfruit.ai.AiDtos.*;
import com.fairyshalltear.freshfruit.common.api.ApiResponse;

@RestController
@RequestMapping("/api/v1/admin/ai/models")
@Validated
public class AiModelController {
    private final AiModelService service;
    public AiModelController(AiModelService service) {this.service=service;}
    @GetMapping public ApiResponse<List<Map<String,Object>>> list(){return ApiResponse.success(service.list());}
    @GetMapping("/{id}") public ApiResponse<Map<String,Object>> detail(@PathVariable @Positive long id){return ApiResponse.success(service.detail(id));}
    @PostMapping public ApiResponse<Map<String,Object>> save(JwtAuthenticationToken jwt,@Valid @RequestBody ModelInput input){return ApiResponse.success(service.save(subject(jwt),input));}
    @PostMapping("/{id}/activate") public ApiResponse<Map<String,Object>> activate(JwtAuthenticationToken jwt,@PathVariable @Positive long id){return ApiResponse.success(service.activate(subject(jwt),id));}
    @PostMapping("/{id}/disable") public ApiResponse<Map<String,Object>> disable(JwtAuthenticationToken jwt,@PathVariable @Positive long id){return ApiResponse.success(service.disable(subject(jwt),id));}
    @DeleteMapping("/{id}") public ApiResponse<Map<String,Object>> delete(JwtAuthenticationToken jwt,@PathVariable @Positive long id){return ApiResponse.success(service.delete(subject(jwt),id));}
    @PostMapping("/{id}/test") public ApiResponse<List<Capability>> test(JwtAuthenticationToken jwt,@PathVariable @Positive long id,@RequestParam(defaultValue="all") String capability){return ApiResponse.success(service.test(subject(jwt),id,capability));}
    private long subject(JwtAuthenticationToken jwt){return Long.parseLong(jwt.getToken().getSubject());}
}
