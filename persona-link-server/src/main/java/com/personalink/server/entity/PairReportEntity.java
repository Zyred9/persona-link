package com.personalink.server.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 双人聚合报告实体，对应 t_pair_report。
 */
@TableName("t_pair_report")
public class PairReportEntity extends BaseAssessmentEntity {

    /** 报告业务编号。 */
    private String reportNo;
    /** 配对会话 ID。 */
    private Long pairSessionId;
    /** 双人聚合报告快照。 */
    private String resultSnapshot;
    /** 生成时间。 */
    private LocalDateTime generatedAt;

    public String getReportNo() { return this.reportNo; }
    public void setReportNo(String reportNo) { this.reportNo = reportNo; }
    public Long getPairSessionId() { return this.pairSessionId; }
    public void setPairSessionId(Long pairSessionId) { this.pairSessionId = pairSessionId; }
    public String getResultSnapshot() { return this.resultSnapshot; }
    public void setResultSnapshot(String resultSnapshot) { this.resultSnapshot = resultSnapshot; }
    public LocalDateTime getGeneratedAt() { return this.generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
}
