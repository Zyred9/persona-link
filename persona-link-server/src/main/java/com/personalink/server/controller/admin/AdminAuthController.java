package com.personalink.server.controller.admin;

import lombok.RequiredArgsConstructor;

import com.personalink.server.dto.ApiResponse;
import com.personalink.server.dto.AdminLoginRequest;
import com.personalink.server.dto.AdminLoginResponse;
import com.personalink.server.dto.AdminProfileResponse;
import com.personalink.server.service.AdminAuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台鉴权接口。
 */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    /**
     * 后台账号登录。
     *
     * @param request 登录账号与密码
     * @return 会话令牌及账号信息
     */
    @PostMapping("/login")
    public ApiResponse<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        return ApiResponse.success(this.adminAuthService.login(request));
    }

    /**
     * 查询当前后台账号。
     *
     * @param authorization Bearer 会话令牌
     * @return 当前账号信息
     */
    @GetMapping("/me")
    public ApiResponse<AdminProfileResponse> me(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(this.adminAuthService.me(authorization));
    }

    /**
     * 注销当前后台会话。
     *
     * @param authorization Bearer 会话令牌
     * @return 空响应
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        this.adminAuthService.logout(authorization);
        return ApiResponse.success(null);
    }
}
