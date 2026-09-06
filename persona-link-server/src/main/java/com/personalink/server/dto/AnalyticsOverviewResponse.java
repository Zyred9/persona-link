package com.personalink.server.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 运营数据总览。
 */
public record AnalyticsOverviewResponse(
        int days,
        LocalDateTime updatedAt,
        AnalyticsMetricsResponse metrics,
        List<AnalyticsTrendResponse> trend,
        List<TestUsageResponse> topTests,
        List<WechatMetricDayResponse> officialTrend) {
}
