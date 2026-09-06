package com.personalink.server.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 答题会话实体，对应 t_answer_session。
 */
@TableName("t_answer_session")
public class AnswerSessionEntity extends BaseAssessmentEntity {

    /** 答卷业务编号。 */
    private String answerNo;
    /** 创建幂等请求号。 */
    private String createRequestId;
    /** 微信 OpenID。 */
    private String openId;
    /** 锁定的题型版本 ID。 */
    private Long versionId;
    /** 答卷类型：1单人，2双人参与者。 */
    private Integer answerType;
    /** 答卷状态。 */
    private Integer answerStatus;
    /** 提交幂等请求号。 */
    private String submitRequestId;
    /** 提交时间。 */
    private LocalDateTime submittedAt;
    /** 报告生成时间。 */
    private LocalDateTime reportReadyAt;

    public String getAnswerNo() { return this.answerNo; }
    public void setAnswerNo(String answerNo) { this.answerNo = answerNo; }
    public String getCreateRequestId() { return this.createRequestId; }
    public void setCreateRequestId(String createRequestId) { this.createRequestId = createRequestId; }
    public String getOpenId() { return this.openId; }
    public void setOpenId(String openId) { this.openId = openId; }
    public Long getVersionId() { return this.versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public Integer getAnswerType() { return this.answerType; }
    public void setAnswerType(Integer answerType) { this.answerType = answerType; }
    public Integer getAnswerStatus() { return this.answerStatus; }
    public void setAnswerStatus(Integer answerStatus) { this.answerStatus = answerStatus; }
    public String getSubmitRequestId() { return this.submitRequestId; }
    public void setSubmitRequestId(String submitRequestId) { this.submitRequestId = submitRequestId; }
    public LocalDateTime getSubmittedAt() { return this.submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public LocalDateTime getReportReadyAt() { return this.reportReadyAt; }
    public void setReportReadyAt(LocalDateTime reportReadyAt) { this.reportReadyAt = reportReadyAt; }
}
