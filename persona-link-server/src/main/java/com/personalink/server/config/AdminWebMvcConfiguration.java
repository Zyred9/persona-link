package com.personalink.server.config;

import com.personalink.server.interceptor.AdminAuthInterceptor;
import com.personalink.server.service.AdminAuthService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 后台接口鉴权配置。
 */
@Configuration
public class AdminWebMvcConfiguration implements WebMvcConfigurer {

    private final ObjectProvider<AdminAuthService> adminAuthServiceProvider;

    public AdminWebMvcConfiguration(ObjectProvider<AdminAuthService> adminAuthServiceProvider) {
        this.adminAuthServiceProvider = adminAuthServiceProvider;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminAuthInterceptor(this.adminAuthServiceProvider))
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns(
                        "/api/admin/auth/login",
                        "/api/admin/auth/me",
                        "/api/admin/auth/logout");
    }
}
