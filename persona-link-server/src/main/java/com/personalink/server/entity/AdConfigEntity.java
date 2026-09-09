package com.personalink.server.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
/** t_ad_config；广告领域持久状态。 @author persona-link @since 2026-09-06 */
@TableName("t_ad_config")
public class AdConfigEntity extends BaseAssessmentEntity {
    /** 广告开关。 */
    private Boolean enabled;
    public Boolean getEnabled() { return this.enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    /** 激励视频广告位 ID。 */
    private String adUnitId;
    public String getAdUnitId() { return this.adUnitId; }
    public void setAdUnitId(String adUnitId) { this.adUnitId = adUnitId; }
    /** 失败策略：1免费，2重试。 */
    private Integer failurePolicy;
    public Integer getFailurePolicy() { return this.failurePolicy; }
    public void setFailurePolicy(Integer failurePolicy) { this.failurePolicy = failurePolicy; }
    /** 最后操作人名称快照。 */
    private String updatedByName;
    public String getUpdatedByName() { return this.updatedByName; }
    public void setUpdatedByName(String updatedByName) { this.updatedByName = updatedByName; }
    /** 最后保存时间。 */
    private LocalDateTime updatedAt;
    public LocalDateTime getUpdatedAt() { return this.updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
