package com.fairyshalltear.freshfruit.audit;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.fairyshalltear.freshfruit.security.AdminAuthorizationInterceptor;

@Configuration
public class AuditWebConfig implements WebMvcConfigurer {
    private final AdminWriteAuditInterceptor interceptor;
    private final AdminAuthorizationInterceptor authorization;

    public AuditWebConfig(AdminWriteAuditInterceptor interceptor, AdminAuthorizationInterceptor authorization) {
        this.interceptor = interceptor;
        this.authorization = authorization;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authorization).addPathPatterns("/api/v1/admin/**");
        registry.addInterceptor(interceptor).addPathPatterns("/api/v1/admin/**");
    }
}
