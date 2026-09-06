package com.personalink.server.miniapp.analytics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** 微信小程序官方数据分析客户端。 */
@Component
public class WechatAnalyticsClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(WechatAnalyticsClient.class);
    private static final DateTimeFormatter WECHAT_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final String appId;
    private final String appSecret;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private volatile String accessToken;
    private volatile Instant accessTokenExpiresAt = Instant.EPOCH;

    public WechatAnalyticsClient(@Value("${WECHAT_APP_ID:}") String appId,
                                 @Value("${WECHAT_APP_SECRET:}") String appSecret,
                                 ObjectMapper objectMapper) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl("https://api.weixin.qq.com").build();
    }

    public WechatDailyMetric getDailyVisitTrend(LocalDate statDate) {
        this.requireConfigured();
        String date = statDate.format(WECHAT_DATE);
        try {
            String responseBody = this.restClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/datacube/getweanalysisappiddailyvisittrend")
                            .queryParam("access_token", this.getAccessToken()).build())
                    .body(Map.of("begin_date", date, "end_date", date))
                    .retrieve()
                    .body(String.class);
            return this.parseDailyMetric(responseBody, statDate);
        } catch (RestClientException exception) {
            LOGGER.error("[微信统计] 日访问趋势调用失败，统计日期：{}", statDate, exception);
            throw new BusinessException(HttpStatus.BAD_GATEWAY, 50231, "微信统计服务暂不可用");
        }
    }

    synchronized String getAccessToken() {
        if (StringUtils.hasText(this.accessToken) && Instant.now().isBefore(this.accessTokenExpiresAt)) {
            return this.accessToken;
        }
        try {
            String responseBody = this.restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/cgi-bin/token")
                            .queryParam("grant_type", "client_credential")
                            .queryParam("appid", this.appId)
                            .queryParam("secret", this.appSecret)
                            .build())
                    .retrieve()
                    .body(String.class);
            JsonNode root = this.parseJson(responseBody);
            this.raiseWechatError(root);
            String token = root.path("access_token").asText();
            if (!StringUtils.hasText(token)) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY, 50231, "微信凭证响应异常");
            }
            int expiresIn = root.path("expires_in").asInt(7200);
            this.accessToken = token;
            this.accessTokenExpiresAt = Instant.now().plusSeconds(Math.max(60, expiresIn - 300L));
            return token;
        } catch (RestClientException exception) {
            LOGGER.error("[微信统计] access_token 调用失败", exception);
            throw new BusinessException(HttpStatus.BAD_GATEWAY, 50231, "微信统计服务暂不可用");
        }
    }

    WechatDailyMetric parseDailyMetric(String responseBody, LocalDate expectedDate) {
        JsonNode root = this.parseJson(responseBody);
        this.raiseWechatError(root);
        JsonNode item = root.path("list").path(0);
        if (item.isMissingNode()) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, 50232, "微信未返回指定日期的统计数据");
        }
        String refDate = item.path("ref_date").asText();
        LocalDate statDate = StringUtils.hasText(refDate) ? LocalDate.parse(refDate, WECHAT_DATE) : expectedDate;
        if (!expectedDate.equals(statDate)) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, 50232, "微信返回的统计日期不匹配");
        }
        return new WechatDailyMetric(statDate, item.path("session_cnt").asInt(), item.path("visit_pv").asInt(),
                item.path("visit_uv").asInt(), item.path("visit_uv_new").asInt(),
                this.decimal(item, "stay_time_uv"), this.decimal(item, "stay_time_session"),
                this.decimal(item, "visit_depth"));
    }

    private JsonNode parseJson(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, 50231, "微信统计响应异常");
        }
        try {
            return this.objectMapper.readTree(responseBody);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, 50231, "微信统计响应异常");
        }
    }

    private void raiseWechatError(JsonNode root) {
        int errorCode = root.path("errcode").asInt(0);
        if (errorCode != 0) {
            LOGGER.warn("[微信统计] 微信接口返回错误，错误码：{}", errorCode);
            throw new BusinessException(HttpStatus.BAD_GATEWAY, 50233, "微信统计同步失败");
        }
    }

    private BigDecimal decimal(JsonNode item, String field) {
        return item.hasNonNull(field) ? item.path(field).decimalValue() : BigDecimal.ZERO;
    }

    private void requireConfigured() {
        if (!StringUtils.hasText(this.appId) || !StringUtils.hasText(this.appSecret)) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, 50331, "微信小程序 AppID 或 AppSecret 未配置");
        }
    }
}
