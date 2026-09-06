package com.personalink.server.dto;

/**
 * 运营核心指标。
 */
public class AnalyticsMetricsResponse {
    private Long uv;
    private Long pv;
    private Long startedCount;
    private Long completedCount;
    private Long shareCount;
    private Long reportCount;

    public Long getUv() { return this.uv; }
    public void setUv(Long uv) { this.uv = uv; }
    public Long getPv() { return this.pv; }
    public void setPv(Long pv) { this.pv = pv; }
    public Long getStartedCount() { return this.startedCount; }
    public void setStartedCount(Long startedCount) { this.startedCount = startedCount; }
    public Long getCompletedCount() { return this.completedCount; }
    public void setCompletedCount(Long completedCount) { this.completedCount = completedCount; }
    public Long getShareCount() { return this.shareCount; }
    public void setShareCount(Long shareCount) { this.shareCount = shareCount; }
    public Long getReportCount() { return this.reportCount; }
    public void setReportCount(Long reportCount) { this.reportCount = reportCount; }
}
