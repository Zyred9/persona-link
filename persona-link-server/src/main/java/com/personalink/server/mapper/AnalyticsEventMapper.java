package com.personalink.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalink.server.dto.AnalyticsMetricsResponse;
import com.personalink.server.dto.AnalyticsTrendResponse;
import com.personalink.server.dto.TestUsageResponse;
import com.personalink.server.entity.AnalyticsEventEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 小程序事件与运营统计数据访问。
 */
@Mapper
public interface AnalyticsEventMapper extends BaseMapper<AnalyticsEventEntity> {
    int insertIgnoreBatch(@Param("items") List<AnalyticsEventEntity> items);
    void upsertVisitor(@Param("openId") String openId, @Param("visitTime") LocalDateTime visitTime);
    void upsertVisitorDay(@Param("statDate") LocalDate statDate,
                          @Param("openId") String openId,
                          @Param("visitTime") LocalDateTime visitTime,
                          @Param("pvIncrement") int pvIncrement);
    AnalyticsMetricsResponse selectMetrics(@Param("since") LocalDateTime since);
    List<AnalyticsTrendResponse> selectTrend(@Param("since") LocalDateTime since);
    List<TestUsageResponse> selectTestUsage(@Param("since") LocalDateTime since);
}
