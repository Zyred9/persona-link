package com.personalink.server.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.personalink.server.dto.AnalyticsEventBatchRequest;
import com.personalink.server.dto.AnalyticsEventBatchResponse;
import com.personalink.server.dto.AnalyticsOverviewResponse;
import com.personalink.server.entity.AnalyticsEventEntity;
import com.personalink.server.dto.MiniappSessionContext;

/**
 * 访问事件与运营统计服务。
 */
public interface AnalyticsService extends IService<AnalyticsEventEntity> {
    AnalyticsEventBatchResponse receive(AnalyticsEventBatchRequest request, MiniappSessionContext context);
    AnalyticsOverviewResponse overview(int days);
    void cleanupExpiredEvents();
}
