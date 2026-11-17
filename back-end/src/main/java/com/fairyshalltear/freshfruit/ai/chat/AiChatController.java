package com.fairyshalltear.freshfruit.ai.chat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.fairyshalltear.freshfruit.ai.AiDtos.*;
import com.fairyshalltear.freshfruit.common.api.ApiResponse;

@RestController
@RequestMapping({"/api/v1/public/ai","/api/v1/buyer/ai"})
@Validated
public class AiChatController {
    private final AiConversationService service;private final AiStreamService streams;
    public AiChatController(AiConversationService service,AiStreamService streams){this.service=service;this.streams=streams;}
    @PostMapping("/guest-session") public ApiResponse<GuestSession> guest(HttpServletRequest request){
        if(!request.getRequestURI().startsWith("/api/v1/public/ai/"))throw com.fairyshalltear.freshfruit.ai.AiErrors.missing();
        return ApiResponse.success(service.guest(request.getRemoteAddr()));
    }
    @PostMapping("/conversations") public ApiResponse<Conversation> create(HttpServletRequest request,JwtAuthenticationToken jwt,@Valid @RequestBody NewConversation input){return ApiResponse.success(service.create(actor(request,jwt),input.context()));}
    @GetMapping("/conversations") public ApiResponse<Page<Conversation>> list(HttpServletRequest request,JwtAuthenticationToken jwt,@RequestParam(defaultValue="1") @Min(1) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return ApiResponse.success(service.list(actor(request,jwt),page,size));}
    @GetMapping("/conversations/{id}") public ApiResponse<ConversationDetail> detail(HttpServletRequest request,JwtAuthenticationToken jwt,@PathVariable @Positive long id){return ApiResponse.success(service.detail(actor(request,jwt),id));}
    @DeleteMapping("/conversations/{id}") public ApiResponse<Void> delete(HttpServletRequest request,JwtAuthenticationToken jwt,@PathVariable @Positive long id){service.delete(actor(request,jwt),id);return ApiResponse.success(null);}
    @PostMapping("/conversations/{id}/messages") public ApiResponse<Request> accept(HttpServletRequest request,JwtAuthenticationToken jwt,@PathVariable @Positive long id,@Valid @RequestBody MessageInput input){return ApiResponse.success(service.accept(actor(request,jwt),id,input));}
    @GetMapping("/requests/{id}") public ApiResponse<Request> status(HttpServletRequest request,JwtAuthenticationToken jwt,@PathVariable @Positive long id){return ApiResponse.success(service.request(actor(request,jwt),id));}
    @GetMapping(value="/requests/{id}/stream",produces="text/event-stream") public SseEmitter stream(HttpServletRequest request,JwtAuthenticationToken jwt,@PathVariable @Positive long id){return streams.stream(actor(request,jwt),id);}
    @PostMapping("/requests/{id}/stop") public ApiResponse<Request> stop(HttpServletRequest request,JwtAuthenticationToken jwt,@PathVariable @Positive long id){return ApiResponse.success(service.stop(actor(request,jwt),id));}
    @PostMapping("/requests/{id}/retry") public ApiResponse<Request> retry(HttpServletRequest request,JwtAuthenticationToken jwt,@PathVariable @Positive long id,@Valid @RequestBody RetryInput input){return ApiResponse.success(service.retry(actor(request,jwt),id,input));}
    @PutMapping("/answers/{id}/feedback") public ApiResponse<Void> feedback(HttpServletRequest request,JwtAuthenticationToken jwt,@PathVariable @Positive long id,@Valid @RequestBody FeedbackInput input){service.feedback(actor(request,jwt),id,input);return ApiResponse.success(null);}
    private AiActor actor(HttpServletRequest request,JwtAuthenticationToken jwt){
        if(request.getRequestURI().startsWith("/api/v1/buyer/ai/"))return new AiActor("BUYER",Long.parseLong(jwt.getToken().getSubject()),null,request.getRemoteAddr(),((Number)jwt.getToken().getClaims().getOrDefault("credentialVersion",0L)).longValue());
        return service.guestActor(request.getHeader("X-AI-Guest-Token"),request.getRemoteAddr());
    }
}
