package com.personalink.server.dto;

import java.time.LocalDateTime;

/**
 * 历史报告查询行。
 */
public class ReportHistoryRow {

    private Long reportId;
    private Long answerSessionId;
    private Long testId;
    private Integer versionNo;
    private String title;
    private String resultCode;
    private String resultName;
    private LocalDateTime generatedAt;

    public Long getReportId() { return this.reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }
    public Long getAnswerSessionId() { return this.answerSessionId; }
    public void setAnswerSessionId(Long answerSessionId) { this.answerSessionId = answerSessionId; }
    public Long getTestId() { return this.testId; }
    public void setTestId(Long testId) { this.testId = testId; }
    public Integer getVersionNo() { return this.versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getTitle() { return this.title; }
    public void setTitle(String title) { this.title = title; }
    public String getResultCode() { return this.resultCode; }
    public void setResultCode(String resultCode) { this.resultCode = resultCode; }
    public String getResultName() { return this.resultName; }
    public void setResultName(String resultName) { this.resultName = resultName; }
    public LocalDateTime getGeneratedAt() { return this.generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
}
