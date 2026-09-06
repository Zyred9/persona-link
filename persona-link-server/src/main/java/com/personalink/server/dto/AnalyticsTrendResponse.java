package com.personalink.server.dto;

import java.time.LocalDate;

/**
 * 每日运营趋势。
 */
public class AnalyticsTrendResponse {
    private LocalDate statDate;
    private Long uv;
    private Long pv;
    private Long startedCount;
    private Long completedCount;

    public LocalDate getStatDate() { return this.statDate; }
    public void setStatDate(LocalDate statDate) { this.statDate = statDate; }
    public Long getUv() { return this.uv; }
    public void setUv(Long uv) { this.uv = uv; }
    public Long getPv() { return this.pv; }
    public void setPv(Long pv) { this.pv = pv; }
    public Long getStartedCount() { return this.startedCount; }
    public void setStartedCount(Long startedCount) { this.startedCount = startedCount; }
    public Long getCompletedCount() { return this.completedCount; }
    public void setCompletedCount(Long completedCount) { this.completedCount = completedCount; }
}
