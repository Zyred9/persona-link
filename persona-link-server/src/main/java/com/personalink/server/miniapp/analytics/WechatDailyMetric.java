package com.personalink.server.miniapp.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 微信访问趋势单日指标。 */
public record WechatDailyMetric(
        LocalDate statDate,
        int sessionCount,
        int visitPv,
        int visitUv,
        int visitUvNew,
        BigDecimal stayTimeUv,
        BigDecimal stayTimeSession,
        BigDecimal visitDepth) {
}
