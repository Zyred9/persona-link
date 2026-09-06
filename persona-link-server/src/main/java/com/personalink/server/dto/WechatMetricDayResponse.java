package com.personalink.server.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 微信官方日访问数据。 */
public record WechatMetricDayResponse(
        LocalDate statDate,
        Integer sessionCount,
        Integer visitPv,
        Integer visitUv,
        Integer visitUvNew,
        BigDecimal stayTimeUv,
        BigDecimal stayTimeSession,
        BigDecimal visitDepth,
        Integer syncStatus,
        LocalDateTime syncedAt) {
}
