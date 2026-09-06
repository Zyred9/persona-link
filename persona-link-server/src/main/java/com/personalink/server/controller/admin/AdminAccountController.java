package com.personalink.server.controller.admin;

import com.personalink.server.dto.AdminAccountCreateRequest;
import com.personalink.server.dto.AdminAccountQuery;
import com.personalink.server.dto.AdminAccountResponse;
import com.personalink.server.dto.AdminAccountUpdateRequest;
import com.personalink.server.dto.AdminPasswordResetRequest;
import com.personalink.server.dto.AdminSessionContext;
import com.personalink.server.dto.ApiResponse;
import com.personalink.server.dto.PageResponse;
import com.personalink.server.interceptor.AdminAuthInterceptor;
import com.personalink.server.service.AdminAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 后台账号管理接口。 */
@Validated
@RestController
@RequestMapping("/api/admin/accounts")
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    public AdminAccountController(AdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
    }

    /**
     * 分页查询后台账号。
     *
     * @param query 查询参数
     * @return 账号分页数据
     */
    @GetMapping
    public ApiResponse<PageResponse<AdminAccountResponse>> page(@Valid AdminAccountQuery query) {
        return ApiResponse.success(this.adminAccountService.pageAccounts(query));
    }

    /**
     * 新增后台账号。
     *
     * @param request 账号参数
     * @return 新增后的账号
     */
    @PostMapping
    public ApiResponse<AdminAccountResponse> create(@Valid @RequestBody AdminAccountCreateRequest request) {
        return ApiResponse.success(this.adminAccountService.createAccount(request));
    }

    /**
     * 修改后台账号。
     *
     * @param id 账号 ID
     * @param request 账号参数
     * @param servletRequest HTTP 请求
     * @return 修改后的账号
     */
    @PutMapping("/{id}")
    public ApiResponse<AdminAccountResponse> update(@PathVariable Long id,
                                                     @Valid @RequestBody AdminAccountUpdateRequest request,
                                                     HttpServletRequest servletRequest) {
        AdminSessionContext context = (AdminSessionContext) servletRequest.getAttribute(
                AdminAuthInterceptor.SESSION_CONTEXT_ATTRIBUTE);
        return ApiResponse.success(this.adminAccountService.updateAccount(
                id, request, context.adminAccount().getId()));
    }

    /**
     * 重置后台账号密码，并注销该账号全部会话。
     *
     * @param id 账号 ID
     * @param request 新密码
     * @return 空响应
     */
    @PostMapping("/{id}/password/reset")
    public ApiResponse<Void> resetPassword(@PathVariable Long id,
                                           @Valid @RequestBody AdminPasswordResetRequest request) {
        this.adminAccountService.resetPassword(id, request.password());
        return ApiResponse.success(null);
    }
}
