package com.personalink.server.dto;

import java.math.BigDecimal;

/**
 * 题型使用统计。
 */
public class TestUsageResponse {
    private Long testId;
    private String testName;
    private Long startedCount;
    private Long completedCount;
    private BigDecimal completionRate;

    public String getTestId() { return this.testId == null ? null : this.testId.toString(); }
    public void setTestId(Long testId) { this.testId = testId; }
    public String getTestName() { return this.testName; }
    public void setTestName(String testName) { this.testName = testName; }
    public Long getStartedCount() { return this.startedCount; }
    public void setStartedCount(Long startedCount) { this.startedCount = startedCount; }
    public Long getCompletedCount() { return this.completedCount; }
    public void setCompletedCount(Long completedCount) { this.completedCount = completedCount; }
    public BigDecimal getCompletionRate() { return this.completionRate; }
    public void setCompletionRate(BigDecimal completionRate) { this.completionRate = completionRate; }
}
