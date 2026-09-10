package com.personalink.server.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 单人报告实体，对应 t_report。
 */
@TableName("t_report")
public class ReportEntity extends BaseAssessmentEntity {

    /** 报告业务编号。 */
    private String reportNo;
    /** 答题会话 ID。 */
    private Long answerSessionId;
    /** 命中的结果编码。 */
    private String resultCode;
    /** 报告生成时的题型封面快照。 */
    private String coverUrl;
    /** 生成时的报告快照。 */
    private String resultSnapshot;
    /** 生成时间。 */
    private LocalDateTime generatedAt;

    public String getReportNo() { return this.reportNo; }
    public void setReportNo(String reportNo) { this.reportNo = reportNo; }
    public Long getAnswerSessionId() { return this.answerSessionId; }
    public void setAnswerSessionId(Long answerSessionId) { this.answerSessionId = answerSessionId; }
    public String getResultCode() { return this.resultCode; }
    public void setResultCode(String resultCode) { this.resultCode = resultCode; }
    public String getCoverUrl() { return this.coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public String getResultSnapshot() { return this.resultSnapshot; }
    public void setResultSnapshot(String resultSnapshot) { this.resultSnapshot = resultSnapshot; }
    public LocalDateTime getGeneratedAt() { return this.generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
}
