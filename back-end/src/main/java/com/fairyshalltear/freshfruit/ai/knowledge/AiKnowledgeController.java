package com.fairyshalltear.freshfruit.ai.knowledge;

import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.fairyshalltear.freshfruit.ai.AiDtos.*;
import com.fairyshalltear.freshfruit.common.api.ApiResponse;

@RestController
@RequestMapping("/api/v1/admin/ai/knowledge")
@Validated
public class AiKnowledgeController {
    private final com.fairyshalltear.freshfruit.ai.AiCatalogService catalog;private final AiKnowledgeService service;private final AiKnowledgeRetriever retriever;
    public AiKnowledgeController(AiKnowledgeService service,AiKnowledgeRetriever retriever,com.fairyshalltear.freshfruit.ai.AiCatalogService catalog){this.service=service;this.retriever=retriever;this.catalog=catalog;}
    @GetMapping public ApiResponse<Page<Knowledge>> list(@RequestParam(defaultValue="1") @Min(1) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size,@RequestParam(required=false) String type,@RequestParam(required=false) String keyword){return ApiResponse.success(service.list(page,size,type,keyword));}
    @GetMapping("/{id}") public ApiResponse<Knowledge> detail(@PathVariable @Positive long id){return ApiResponse.success(service.detail(id));}
    @PostMapping public ApiResponse<Knowledge> create(JwtAuthenticationToken jwt,@Valid @RequestBody KnowledgeInput input){return ApiResponse.success(service.save(subject(jwt),null,input));}
    @PutMapping("/{id}") public ApiResponse<Knowledge> save(JwtAuthenticationToken jwt,@PathVariable @Positive long id,@Valid @RequestBody KnowledgeInput input){return ApiResponse.success(service.save(subject(jwt),id,input));}
    @PostMapping("/{id}/publish") public ApiResponse<Knowledge> publish(JwtAuthenticationToken jwt,@PathVariable @Positive long id,@Valid @RequestBody VersionInput input){return ApiResponse.success(service.publish(subject(jwt),id,input.version()));}
    @PatchMapping("/{id}/enabled") public ApiResponse<Knowledge> enabled(JwtAuthenticationToken jwt,@PathVariable @Positive long id,@Valid @RequestBody EnabledInput input){return ApiResponse.success(service.enabled(subject(jwt),id,input));}
    @PostMapping("/{id}/retry") public ApiResponse<Knowledge> retry(JwtAuthenticationToken jwt,@PathVariable @Positive long id,@Valid @RequestBody VersionInput input){return ApiResponse.success(service.retry(subject(jwt),id,input.version()));}
    @PostMapping("/search-test") public ApiResponse<Map<String,Object>> search(@Valid @RequestBody SearchInput input){var result=retriever.search(input.question(),true);return ApiResponse.success(Map.of("mode",result.mode(),"hits",result.hits().stream().map(h->Map.of("source",h.source(),"keywordScore",h.keywordScore(),"vectorScore",h.vectorScore(),"exactFaq",h.exactFaq())).toList()));}
    @GetMapping("/products") public ApiResponse<List<Map<String,Object>>> products(){return ApiResponse.success(catalog.products().stream().map(p->com.fairyshalltear.freshfruit.ai.persistence.AiMapper.values("id",p.id(),"name",p.text("name"),"facts",catalog.baseFacts(p))).toList());}
    @GetMapping("/business-policy") public ApiResponse<String> policy(){return ApiResponse.success("已付款且未发货的订单可申请退款；签收后7天内可申请退货。当前系统不支持换货，物流和退款为模拟流程。商品质量等具体问题需按现有售后页面提交审核，不承诺自动审核或实际到账时间。");}
    private long subject(JwtAuthenticationToken jwt){return Long.parseLong(jwt.getToken().getSubject());}
}
