package com.personalink.server.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
/** t_report_ad_task；广告领域持久状态。 @author persona-link @since 2026-09-06 */
@TableName("t_report_ad_task")
public class ReportAdTaskEntity extends BaseAssessmentEntity {
    /** 任务用户。 */
    private String openId;
    public String getOpenId() { return this.openId; }
    public void setOpenId(String openId) { this.openId = openId; }
    /** 报告类型：1单人，2双人。 */
    private Integer reportType;
    public Integer getReportType() { return this.reportType; }
    public void setReportType(Integer reportType) { this.reportType = reportType; }
    /** 单人报告 ID 或双人会话 ID。 */
    private Long reportId;
    public Long getReportId() { return this.reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }
    /** 不可预测的一次性任务号。 */
    private String taskId;
    public String getTaskId() { return this.taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    /** 发起时广告位。 */
    private String adUnitId;
    public String getAdUnitId() { return this.adUnitId; }
    public void setAdUnitId(String adUnitId) { this.adUnitId = adUnitId; }
    /** 失效时间。 */
    private LocalDateTime expiresAt;
    public LocalDateTime getExpiresAt() { return this.expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    /** 使用时间。 */
    private LocalDateTime consumedAt;
    public LocalDateTime getConsumedAt() { return this.consumedAt; }
    public void setConsumedAt(LocalDateTime consumedAt) { this.consumedAt = consumedAt; }
    /** 使用结果：1完成，2失败。 */
    private Integer outcome;
    public Integer getOutcome() { return this.outcome; }
    public void setOutcome(Integer outcome) { this.outcome = outcome; }
    /** 微信广告失败码。 */
    private Integer errorCode;
    public Integer getErrorCode() { return this.errorCode; }
    public void setErrorCode(Integer errorCode) { this.errorCode = errorCode; }
}
