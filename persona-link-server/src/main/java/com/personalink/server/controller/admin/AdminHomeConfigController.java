package com.personalink.server.controller.admin;

import com.personalink.server.dto.ApiResponse;
import com.personalink.server.dto.HomeConfigResponse;
import com.personalink.server.dto.HomeConfigSaveRequest;
import com.personalink.server.service.AppConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 首页公共配置，沿用后台内容运营读写权限。 */
@RestController
@RequestMapping("/api/admin/home-config")
@RequiredArgsConstructor
public class AdminHomeConfigController {
    private final AppConfigService service;

    /**
     * 查询首页公共配置。
     * @return 首页标题图配置
     */
    @GetMapping
    public ApiResponse<HomeConfigResponse> read() {
        return ApiResponse.success(this.service.readHomeConfig());
    }

    /**
     * 保存首页公共配置，下一次首页查询即生效。
     * @param request 首页标题图地址
     * @return 已保存的首页配置
     */
    @PutMapping
    public ApiResponse<HomeConfigResponse> save(@Valid @RequestBody HomeConfigSaveRequest request) {
        return ApiResponse.success(this.service.saveHomeConfig(request));
    }
}
