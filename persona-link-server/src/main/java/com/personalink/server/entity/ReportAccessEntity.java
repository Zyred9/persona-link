package com.personalink.server.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
/** t_report_access；广告领域持久状态。 @author persona-link @since 2026-09-06 */
@TableName("t_report_access")
public class ReportAccessEntity extends BaseAssessmentEntity {
    /** 授权用户。 */
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
    /** 授权来源：1广告关闭，2观看完成，3失败放行，4旧报告迁移。 */
    private Integer grantSource;
    public Integer getGrantSource() { return this.grantSource; }
    public void setGrantSource(Integer grantSource) { this.grantSource = grantSource; }
    /** 授予时间。 */
    private LocalDateTime grantedAt;
    public LocalDateTime getGrantedAt() { return this.grantedAt; }
    public void setGrantedAt(LocalDateTime grantedAt) { this.grantedAt = grantedAt; }
}
