package com.personalink.server.controller.job;

import com.personalink.server.dto.ApiResponse;
import com.personalink.server.dto.WechatMetricDayResponse;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.service.AnalyticsService;
import com.personalink.server.service.ContentService;
import com.personalink.server.service.WechatMetricDayService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;

/** 供外部 XXL-JOB 调用的系统任务接口。 */
@RestController
@RequestMapping("/api/job")
public class SystemJobController {

    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");
    private final ContentService contentService;
    private final AnalyticsService analyticsService;
    private final WechatMetricDayService wechatMetricDayService;
    private final String jobToken;

    public SystemJobController(ContentService contentService,
                               AnalyticsService analyticsService,
                               WechatMetricDayService wechatMetricDayService,
                               @Value("${PERSONA_LINK_JOB_TOKEN:}") String jobToken) {
        this.contentService = contentService;
        this.analyticsService = analyticsService;
        this.wechatMetricDayService = wechatMetricDayService;
        this.jobToken = jobToken;
    }

    /**
     * 发布已到计划时间的题型版本。
     * <p>cron: 0 * * * * ?</p>
     *
     * @param token 任务调用令牌
     * @return 发布数量
     */
    @PostMapping("/test-versions/publish-due")
    public ApiResponse<Integer> publishDue(@RequestHeader("X-Job-Token") String token) {
        this.requireValidToken(token);
        return ApiResponse.success(this.contentService.publishDueVersions());
    }

    /**
     * 清理超出保留期的小程序事件。
     * <p>cron: 0 30 2 * * ?</p>
     *
     * @param token 任务调用令牌
     * @return 空响应
     */
    @PostMapping("/analytics/events/cleanup")
    public ApiResponse<Void> cleanupAnalyticsEvents(@RequestHeader("X-Job-Token") String token) {
        this.requireValidToken(token);
        this.analyticsService.cleanupExpiredEvents();
        return ApiResponse.success(null);
    }

    /**
     * 同步微信官方昨日访问数据。
     * <p>cron: 0 10 8 * * ?</p>
     *
     * @param token 任务调用令牌
     * @return 同步后的日指标
     */
    @PostMapping("/analytics/wechat/sync-yesterday")
    public ApiResponse<WechatMetricDayResponse> syncWechatYesterday(
            @RequestHeader("X-Job-Token") String token) {
        this.requireValidToken(token);
        LocalDate yesterday = LocalDate.now(CHINA_ZONE).minusDays(1);
        return ApiResponse.success(this.wechatMetricDayService.sync(yesterday));
    }

    private void requireValidToken(String token) {
        if (this.jobToken.isBlank()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, 50341, "任务调用令牌未配置");
        }
        if (!MessageDigest.isEqual(this.jobToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, 40141, "任务调用令牌无效");
        }
    }
}
