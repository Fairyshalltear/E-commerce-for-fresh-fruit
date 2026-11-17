package com.fairyshalltear.freshfruit.ai.model;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.fairyshalltear.freshfruit.ai.knowledge.AiProfileService;
import com.fairyshalltear.freshfruit.common.api.ApiResponse;

@RestController
@RequestMapping("/api/v1/admin/ai/models")
public class AiProfileController {
    private final AiProfileService service;
    public AiProfileController(AiProfileService service){this.service=service;}
    @GetMapping("/profiles") public ApiResponse<List<Map<String,Object>>> list(){return ApiResponse.success(service.list());}
    @PostMapping("/{id}/profiles") public ApiResponse<Map<String,Long>> build(JwtAuthenticationToken jwt,@PathVariable long id){return ApiResponse.success(Map.of("profileId",service.build(subject(jwt),id)));}
    @PostMapping("/profiles/{id}/switch") public ApiResponse<Void> switchTo(JwtAuthenticationToken jwt,@PathVariable long id){service.switchTo(subject(jwt),id);return ApiResponse.success(null);}
    @PostMapping("/profiles/{id}/cancel") public ApiResponse<Void> cancel(JwtAuthenticationToken jwt,@PathVariable long id){service.cancel(subject(jwt),id);return ApiResponse.success(null);}
    private long subject(JwtAuthenticationToken jwt){return Long.parseLong(jwt.getToken().getSubject());}
}
