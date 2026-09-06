package com.personalink.server.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 小程序事件批量上报请求。
 */
public record AnalyticsEventBatchRequest(
        @NotEmpty @Size(max = 100) List<@Valid AnalyticsEventRequest> events) {
}
