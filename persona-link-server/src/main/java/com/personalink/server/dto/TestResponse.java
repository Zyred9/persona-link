package com.personalink.server.dto;

import java.time.LocalDateTime;

/** 题型响应。 */
public class TestResponse {
    private Long id;
    private String testName;
    private Integer testType;
    private Long categoryId;
    private String categoryName;
    private String coverUrl;
    private Integer drawQuestionCount;
    private Integer estimatedMinutes;
    private Integer status;
    private Integer currentVersionNo;
    private Integer currentVersionStatus;
    private Integer homeDisplay;
    private Integer homeSort;
    private LocalDateTime updateDate;

    public Long getId() { return this.id; }
    public void setId(Long id) { this.id = id; }
    public String getTestName() { return this.testName; }
    public void setTestName(String testName) { this.testName = testName; }
    public Integer getTestType() { return this.testType; }
    public void setTestType(Integer testType) { this.testType = testType; }
    public Long getCategoryId() { return this.categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return this.categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getCoverUrl() { return this.coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public Integer getDrawQuestionCount() { return this.drawQuestionCount; }
    public void setDrawQuestionCount(Integer drawQuestionCount) { this.drawQuestionCount = drawQuestionCount; }
    public Integer getEstimatedMinutes() { return this.estimatedMinutes; }
    public void setEstimatedMinutes(Integer estimatedMinutes) { this.estimatedMinutes = estimatedMinutes; }
    public Integer getStatus() { return this.status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getCurrentVersionNo() { return this.currentVersionNo; }
    public void setCurrentVersionNo(Integer currentVersionNo) { this.currentVersionNo = currentVersionNo; }
    public Integer getCurrentVersionStatus() { return this.currentVersionStatus; }
    public void setCurrentVersionStatus(Integer currentVersionStatus) { this.currentVersionStatus = currentVersionStatus; }
    public Integer getHomeDisplay() { return this.homeDisplay; }
    public void setHomeDisplay(Integer homeDisplay) { this.homeDisplay = homeDisplay; }
    public Integer getHomeSort() { return this.homeSort; }
    public void setHomeSort(Integer homeSort) { this.homeSort = homeSort; }
    public LocalDateTime getUpdateDate() { return this.updateDate; }
    public void setUpdateDate(LocalDateTime updateDate) { this.updateDate = updateDate; }
}
