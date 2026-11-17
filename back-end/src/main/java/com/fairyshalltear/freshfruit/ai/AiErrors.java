package com.fairyshalltear.freshfruit.ai;

import org.springframework.http.HttpStatus;
import com.fairyshalltear.freshfruit.common.api.ApiException;

public final class AiErrors {
    private AiErrors() {}
    public static ApiException invalid(String message) { return new ApiException("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST); }
    public static ApiException missing() { return new ApiException("NOT_FOUND", "当前内容不存在或不可访问", HttpStatus.NOT_FOUND); }
    public static ApiException conflict(String message) { return new ApiException("AI_CONFLICT", message, HttpStatus.CONFLICT); }
    public static ApiException unavailable(String code, String message) { return new ApiException(code, message, HttpStatus.SERVICE_UNAVAILABLE); }
}
