package com.personalink.server.controller.admin;

import com.personalink.server.dto.AnalyticsOverviewResponse;
import com.personalink.server.dto.ApiResponse;
import com.personalink.server.service.AnalyticsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台运营数据接口。
 */
@Validated
@RestController
@RequestMapping("/api/admin")
public class AdminAnalyticsController {

    private final AnalyticsService analyticsService;

    public AdminAnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * 查询运营数据总览。
     *
     * @param days 统计天数
     * @return 核心指标、趋势和题型使用情况
     */
    @GetMapping("/analytics/overview")
    public ApiResponse<AnalyticsOverviewResponse> overview(
            @RequestParam(defaultValue = "7") @Min(1) @Max(30) int days) {
        return ApiResponse.success(this.analyticsService.overview(days));
    }

    /**
     * 查询运营后台首页数据。
     *
     * @param days 统计天数
     * @return 运营数据总览
     */
    @GetMapping("/dashboard")
    public ApiResponse<AnalyticsOverviewResponse> dashboard(
            @RequestParam(defaultValue = "7") @Min(1) @Max(30) int days) {
        return ApiResponse.success(this.analyticsService.overview(days));
    }
}
