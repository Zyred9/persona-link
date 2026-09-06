package com.personalink.server.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 题型版本实体，对应 t_test_version。 */
@TableName("t_test_version")
public class TestVersionEntity extends BaseAssessmentEntity {
    /** 题型 ID。 */
    private Long testId;
    /** 版本号。 */
    private Integer versionNo;
    /** 展示标题。 */
    private String title;
    /** 封面地址。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String coverUrl;
    /** 详情整图地址，与首页封面独立的版本快照。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String detailImageUrl;
    /** 题型说明。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    /** 预计分钟数。 */
    private Integer estimatedMinutes;
    /** 单次抽题数。 */
    private Integer drawQuestionCount;
    /** 版本状态。 */
    private Integer versionStatus;
    /** 版本说明。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String versionNote;
    /** 审核驳回原因。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String reviewReason;
    /** 计划发布时间。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime scheduledAt;
    /** 实际发布时间。 */
    private LocalDateTime publishedAt;
    /** 下线时间。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime offlineAt;
    /** 风险下线标记。 */
    private Integer riskOfflineFlag;
    public Long getTestId() { return this.testId; }
    public void setTestId(Long testId) { this.testId = testId; }
    public Integer getVersionNo() { return this.versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getTitle() { return this.title; }
    public void setTitle(String title) { this.title = title; }
    public String getCoverUrl() { return this.coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public String getDetailImageUrl() { return this.detailImageUrl; }
    public void setDetailImageUrl(String detailImageUrl) { this.detailImageUrl = detailImageUrl; }
    public String getDescription() { return this.description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getEstimatedMinutes() { return this.estimatedMinutes; }
    public void setEstimatedMinutes(Integer estimatedMinutes) { this.estimatedMinutes = estimatedMinutes; }
    public Integer getDrawQuestionCount() { return this.drawQuestionCount; }
    public void setDrawQuestionCount(Integer drawQuestionCount) { this.drawQuestionCount = drawQuestionCount; }
    public Integer getVersionStatus() { return this.versionStatus; }
    public void setVersionStatus(Integer versionStatus) { this.versionStatus = versionStatus; }
    public String getVersionNote() { return this.versionNote; }
    public void setVersionNote(String versionNote) { this.versionNote = versionNote; }
    public String getReviewReason() { return this.reviewReason; }
    public void setReviewReason(String reviewReason) { this.reviewReason = reviewReason; }
    public LocalDateTime getScheduledAt() { return this.scheduledAt; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public LocalDateTime getPublishedAt() { return this.publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public LocalDateTime getOfflineAt() { return this.offlineAt; }
    public void setOfflineAt(LocalDateTime offlineAt) { this.offlineAt = offlineAt; }
    public Integer getRiskOfflineFlag() { return this.riskOfflineFlag; }
    public void setRiskOfflineFlag(Integer riskOfflineFlag) { this.riskOfflineFlag = riskOfflineFlag; }
}
