package com.personalink.server.controller.admin;

import lombok.RequiredArgsConstructor;
import com.personalink.server.dto.*;
import com.personalink.server.interceptor.AdminAuthInterceptor;
import com.personalink.server.service.AdConfigService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** 管理员专用广告配置接口。 */
@RestController
@RequestMapping("/api/admin/ad-config")
@RequiredArgsConstructor
public class AdminAdConfigController {
    private final AdConfigService service;
    /** 查询已保存配置。
     * @return 广告配置，不代表微信投放状态
     */
    @GetMapping
    public ApiResponse<AdConfigResponse> read() { return ApiResponse.success(this.service.readConfig()); }

    /** 保存配置，后续报告访问立即使用新策略。
     * @param request 配置表单
     * @param servletRequest 已鉴权管理员请求
     * @return 已保存配置
     */
    @PutMapping
    public ApiResponse<AdConfigResponse> save(@Valid @RequestBody AdConfigSaveRequest request,
            HttpServletRequest servletRequest) {
        AdminSessionContext context = (AdminSessionContext) servletRequest.getAttribute(
                AdminAuthInterceptor.SESSION_CONTEXT_ATTRIBUTE);
        return ApiResponse.success(this.service.saveConfig(request, context.adminAccount()));
    }
}
