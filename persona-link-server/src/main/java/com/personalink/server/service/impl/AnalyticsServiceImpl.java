package com.personalink.server.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.personalink.server.dto.AnalyticsEventBatchRequest;
import com.personalink.server.dto.AnalyticsEventBatchResponse;
import com.personalink.server.dto.AnalyticsEventRequest;
import com.personalink.server.dto.AnalyticsMetricsResponse;
import com.personalink.server.dto.AnalyticsOverviewResponse;
import com.personalink.server.entity.AnalyticsEventEntity;
import com.personalink.server.mapper.AnalyticsEventMapper;
import com.personalink.server.service.AnalyticsService;
import com.personalink.server.service.WechatMetricDayService;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.dto.MiniappSessionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 访问事件与运营统计服务实现。
 */
@Service
public class AnalyticsServiceImpl extends ServiceImpl<AnalyticsEventMapper, AnalyticsEventEntity>
        implements AnalyticsService {

    private static final int PAGE_VIEW_EVENT = 2;

    private final int eventRetentionDays;
    private final WechatMetricDayService wechatMetricDayService;

    public AnalyticsServiceImpl(@Value("${app.analytics.event-retention-days:30}") int eventRetentionDays,
                                WechatMetricDayService wechatMetricDayService) {
        this.eventRetentionDays = eventRetentionDays;
        this.wechatMetricDayService = wechatMetricDayService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AnalyticsEventBatchResponse receive(AnalyticsEventBatchRequest request, MiniappSessionContext context) {
        Map<String, AnalyticsEventRequest> uniqueRequests = new LinkedHashMap<>();
        request.events().forEach(event -> uniqueRequests.putIfAbsent(event.eventId(), event));
        Set<String> existingIds = new HashSet<>();
        if (!uniqueRequests.isEmpty()) {
            this.list(Wrappers.<AnalyticsEventEntity>lambdaQuery()
                            .in(AnalyticsEventEntity::getEventId, uniqueRequests.keySet()))
                    .forEach(event -> existingIds.add(event.getEventId()));
        }

        LocalDateTime receivedTime = LocalDateTime.now();
        List<AnalyticsEventEntity> events = uniqueRequests.values().stream()
                .filter(event -> !existingIds.contains(event.eventId()))
                .map(event -> this.toEntity(event, context, receivedTime))
                .toList();
        int insertedCount = events.isEmpty() ? 0 : this.baseMapper.insertIgnoreBatch(events);

        int pageViews = (int) events.stream()
                .filter(event -> PAGE_VIEW_EVENT == event.getEventType())
                .count();
        if (insertedCount == 0) {
            pageViews = 0;
        }
        // ponytail: 并发部分重复时日聚合 PV 可能轻微高估；需要强一致时改为定时按事件表重算。
        this.baseMapper.upsertVisitor(context.openId(), receivedTime);
        this.baseMapper.upsertVisitorDay(receivedTime.toLocalDate(), context.openId(), receivedTime, pageViews);
        int duplicateCount = request.events().size() - insertedCount;
        return new AnalyticsEventBatchResponse(insertedCount, duplicateCount);
    }

    @Override
    @Transactional(readOnly = true)
    public AnalyticsOverviewResponse overview(int days) {
        LocalDateTime since = LocalDateTime.now().toLocalDate().minusDays(days - 1L).atStartOfDay();
        AnalyticsMetricsResponse metrics = this.baseMapper.selectMetrics(since);
        return new AnalyticsOverviewResponse(days, LocalDateTime.now(), metrics,
                this.baseMapper.selectTrend(since), this.baseMapper.selectTestUsage(since),
                this.wechatMetricDayService.listSince(since.toLocalDate()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cleanupExpiredEvents() {
        if (this.eventRetentionDays < 1) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, 50311, "事件保留天数配置无效");
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(this.eventRetentionDays);
        this.baseMapper.delete(Wrappers.<AnalyticsEventEntity>lambdaQuery()
                .lt(AnalyticsEventEntity::getReceivedTime, cutoff));
    }

    private AnalyticsEventEntity toEntity(AnalyticsEventRequest request,
                                           MiniappSessionContext context,
                                           LocalDateTime receivedTime) {
        AnalyticsEventEntity entity = new AnalyticsEventEntity();
        entity.setEventId(request.eventId());
        entity.setOpenId(context.openId());
        entity.setSessionId(context.businessSession().getId().toString());
        entity.setEventType(request.eventType());
        entity.setPagePath(request.pagePath());
        entity.setBusinessId(this.parseBusinessId(request.businessId()));
        entity.setSourceScene(request.sourceScene());
        entity.setChannel(request.channel());
        entity.setAppVersion(request.appVersion());
        entity.setClientTime(request.clientTime());
        entity.setReceivedTime(receivedTime);
        return entity;
    }

    private Long parseBusinessId(String businessId) {
        if (businessId == null) {
            return null;
        }
        try {
            return Long.valueOf(businessId);
        } catch (NumberFormatException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40021, "业务 ID 格式错误");
        }
    }
}
