package com.personalink.server.service.impl;

import lombok.RequiredArgsConstructor;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.personalink.server.dto.WechatMetricDayResponse;
import com.personalink.server.entity.WechatMetricDayEntity;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.mapper.WechatMetricDayMapper;
import com.personalink.server.miniapp.analytics.WechatAnalyticsClient;
import com.personalink.server.miniapp.analytics.WechatDailyMetric;
import com.personalink.server.service.WechatMetricDayService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** 微信官方日访问数据服务实现。 */
@Service
@RequiredArgsConstructor
public class WechatMetricDayServiceImpl extends ServiceImpl<WechatMetricDayMapper, WechatMetricDayEntity>
        implements WechatMetricDayService {

    private final WechatAnalyticsClient wechatAnalyticsClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WechatMetricDayResponse sync(LocalDate statDate) {
        if (statDate.isAfter(LocalDate.now().minusDays(1))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40031, "微信日统计最晚只能同步昨日数据");
        }
        WechatDailyMetric metric = this.wechatAnalyticsClient.getDailyVisitTrend(statDate);
        WechatMetricDayEntity entity = this.getOne(Wrappers.<WechatMetricDayEntity>lambdaQuery()
                .eq(WechatMetricDayEntity::getStatDate, statDate), false);
        if (Objects.isNull(entity)) {
            entity = new WechatMetricDayEntity();
        }
        entity.setStatDate(metric.statDate());
        entity.setSessionCount(metric.sessionCount());
        entity.setVisitPv(metric.visitPv());
        entity.setVisitUv(metric.visitUv());
        entity.setVisitUvNew(metric.visitUvNew());
        entity.setStayTimeUv(metric.stayTimeUv());
        entity.setStayTimeSession(metric.stayTimeSession());
        entity.setVisitDepth(metric.visitDepth());
        entity.setSyncStatus(1);
        entity.setSyncedAt(LocalDateTime.now());
        this.saveOrUpdate(entity);
        return this.toResponse(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WechatMetricDayResponse> listSince(LocalDate since) {
        return this.list(Wrappers.<WechatMetricDayEntity>lambdaQuery()
                        .ge(WechatMetricDayEntity::getStatDate, since)
                        .orderByAsc(WechatMetricDayEntity::getStatDate))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private WechatMetricDayResponse toResponse(WechatMetricDayEntity entity) {
        return new WechatMetricDayResponse(entity.getStatDate(), entity.getSessionCount(), entity.getVisitPv(),
                entity.getVisitUv(), entity.getVisitUvNew(), entity.getStayTimeUv(), entity.getStayTimeSession(),
                entity.getVisitDepth(), entity.getSyncStatus(), entity.getSyncedAt());
    }
}
