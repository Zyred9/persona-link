package com.personalink.server.interceptor;

import com.personalink.server.dto.AdminSessionContext;
import com.personalink.server.service.AdminAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 后台接口默认鉴权拦截器。
 */
public class AdminAuthInterceptor implements HandlerInterceptor {

    public static final String SESSION_CONTEXT_ATTRIBUTE = AdminSessionContext.class.getName();

    private final ObjectProvider<AdminAuthService> adminAuthServiceProvider;

    public AdminAuthInterceptor(ObjectProvider<AdminAuthService> adminAuthServiceProvider) {
        this.adminAuthServiceProvider = adminAuthServiceProvider;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        AdminAuthService adminAuthService = this.adminAuthServiceProvider.getObject();
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        AdminSessionContext context = request.getRequestURI().startsWith("/api/admin/accounts")
                ? adminAuthService.requireAdminSession(authorization)
                : HttpMethod.GET.matches(request.getMethod())
                || HttpMethod.HEAD.matches(request.getMethod())
                ? adminAuthService.requireSession(authorization)
                : adminAuthService.requireWritableSession(authorization);
        request.setAttribute(SESSION_CONTEXT_ATTRIBUTE, context);
        return true;
    }
}
