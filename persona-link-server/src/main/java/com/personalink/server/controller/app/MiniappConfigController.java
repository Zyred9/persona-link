package com.personalink.server.controller.app;

import com.personalink.server.dto.ApiResponse;
import com.personalink.server.dto.MiniappConfigResponse;
import com.personalink.server.service.AppConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 小程序公开展示配置，无需登录。 */
@RestController
@RequestMapping("/api/miniapp/config")
@RequiredArgsConstructor
public class MiniappConfigController {
    private final AppConfigService service;

    /**
     * 读取小程序版本展示信息。
     * @return 公开配置白名单
     */
    @GetMapping
    public ApiResponse<MiniappConfigResponse> read() {
        return ApiResponse.success(this.service.readMiniappConfig());
    }
}
