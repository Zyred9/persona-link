package com.personalink.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 内容操作审计实体，对应 t_content_audit_log。 */
@TableName("t_content_audit_log")
public class ContentAuditLogEntity {
    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 业务类型：1题型，2题型版本，3首页配置，4广告配置。 */
    private Integer bizType;
    /** 业务 ID。 */
    private Long bizId;
    /** 操作类型。 */
    private Integer actionType;
    /** 操作前快照。 */
    private String beforeSnapshot;
    /** 操作后快照。 */
    private String afterSnapshot;
    /** 操作原因。 */
    private String reason;
    /** 操作人 ID。 */
    private Long operatorId;
    /** 操作时间。 */
    private LocalDateTime createDate;

    public Long getId() { return this.id; }
    public void setId(Long id) { this.id = id; }
    public Integer getBizType() { return this.bizType; }
    public void setBizType(Integer bizType) { this.bizType = bizType; }
    public Long getBizId() { return this.bizId; }
    public void setBizId(Long bizId) { this.bizId = bizId; }
    public Integer getActionType() { return this.actionType; }
    public void setActionType(Integer actionType) { this.actionType = actionType; }
    public String getBeforeSnapshot() { return this.beforeSnapshot; }
    public void setBeforeSnapshot(String beforeSnapshot) { this.beforeSnapshot = beforeSnapshot; }
    public String getAfterSnapshot() { return this.afterSnapshot; }
    public void setAfterSnapshot(String afterSnapshot) { this.afterSnapshot = afterSnapshot; }
    public String getReason() { return this.reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Long getOperatorId() { return this.operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public LocalDateTime getCreateDate() { return this.createDate; }
    public void setCreateDate(LocalDateTime createDate) { this.createDate = createDate; }
}
