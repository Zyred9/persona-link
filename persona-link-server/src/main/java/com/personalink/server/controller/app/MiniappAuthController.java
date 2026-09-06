package com.personalink.server.controller.app;

import com.personalink.server.dto.ApiResponse;
import com.personalink.server.dto.WechatLoginRequest;
import com.personalink.server.dto.WechatLoginResponse;
import com.personalink.server.service.impl.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序登录接口。
 */
@RestController
@RequestMapping("/api/miniapp/auth")
public class MiniappAuthController {

    private final AuthService authService;

    public MiniappAuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 使用微信一次性登录凭证换取业务会话。
     *
     * @param request 微信登录凭证
     * @return 业务会话令牌及过期时间
     */
    @PostMapping("/wechat")
    public ApiResponse<WechatLoginResponse> wechatLogin(@Valid @RequestBody WechatLoginRequest request) {
        return ApiResponse.success(this.authService.login(request));
    }
}
