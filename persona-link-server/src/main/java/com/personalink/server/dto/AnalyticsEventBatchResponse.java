package com.personalink.server.dto;

/**
 * 小程序事件接收结果。
 */
public record AnalyticsEventBatchResponse(int acceptedCount, int duplicateCount) {
}
