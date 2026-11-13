package com.fairyshalltear.freshfruit.catalog;
import org.springframework.http.HttpStatus;
import com.fairyshalltear.freshfruit.common.api.ApiException;
public final class CatalogErrors {
    private CatalogErrors() {}
    public static ApiException invalid(String message) { return new ApiException("VALIDATION_ERROR",message,HttpStatus.BAD_REQUEST); }
    public static ApiException conflict(String message) { return new ApiException("CONFLICT",message,HttpStatus.CONFLICT); }
    public static ApiException notFound() { return new ApiException("NOT_FOUND","商品或分类不存在或已不可用",HttpStatus.NOT_FOUND); }
    public static void version(Long expected, Long actual) {
        if(expected==null || !expected.equals(actual))
            throw new ApiException("VERSION_CONFLICT","信息已被修改，请重新读取后提交",HttpStatus.CONFLICT);
    }
}
