package com.personalink.server.controller.app;

import lombok.RequiredArgsConstructor;

import com.personalink.server.dto.AnalyticsEventBatchRequest;
import com.personalink.server.dto.AnalyticsEventBatchResponse;
import com.personalink.server.dto.ApiResponse;
import com.personalink.server.service.AnalyticsService;
import com.personalink.server.service.impl.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序事件上报接口。
 */
@RestController
@RequestMapping("/api/miniapp/events")
@RequiredArgsConstructor
public class MiniappAnalyticsController {

    private final AnalyticsService analyticsService;
    private final AuthService authService;

    /**
     * 批量接收小程序行为事件。
     *
     * @param authorization 小程序业务会话
     * @param request 事件列表
     * @return 接收及去重数量
     */
    @PostMapping("/batch")
    public ApiResponse<AnalyticsEventBatchResponse> receive(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody AnalyticsEventBatchRequest request) {
        return ApiResponse.success(this.analyticsService.receive(
                request, this.authService.requireSession(authorization)));
    }
}
