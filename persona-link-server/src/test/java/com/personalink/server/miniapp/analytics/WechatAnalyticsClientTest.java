package com.personalink.server.miniapp.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WechatAnalyticsClientTest {

    @Test
    void shouldParseDailyVisitTrend() {
        WechatAnalyticsClient client = new WechatAnalyticsClient("", "", new ObjectMapper());

        WechatDailyMetric metric = client.parseDailyMetric("""
                {"list":[{"ref_date":"20260903","session_cnt":12,"visit_pv":30,"visit_uv":18,
                "visit_uv_new":4,"stay_time_uv":9.5,"stay_time_session":5.2,"visit_depth":2.1}]}
                """, LocalDate.of(2026, 9, 3));

        assertEquals(LocalDate.of(2026, 9, 3), metric.statDate());
        assertEquals(12, metric.sessionCount());
        assertEquals(30, metric.visitPv());
        assertEquals(18, metric.visitUv());
        assertEquals(new BigDecimal("2.1"), metric.visitDepth());
    }
}
