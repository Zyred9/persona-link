package com.personalink.server.dto;
import java.time.LocalDateTime;
/** 已保存的广告配置，不代表微信已审核或可投放。 */
public record AdConfigResponse(boolean enabled, String adUnitId, int failurePolicy,
        LocalDateTime updatedAt, String updatedByName) {}
