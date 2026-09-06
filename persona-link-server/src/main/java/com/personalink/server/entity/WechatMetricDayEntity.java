package com.personalink.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 微信官方日访问数据实体，对应 t_wechat_metric_day。 */
@TableName("t_wechat_metric_day")
public class WechatMetricDayEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDate statDate;
    private Integer sessionCount;
    private Integer visitPv;
    private Integer visitUv;
    private Integer visitUvNew;
    private BigDecimal stayTimeUv;
    private BigDecimal stayTimeSession;
    private BigDecimal visitDepth;
    private Integer syncStatus;
    private LocalDateTime syncedAt;

    public Long getId() { return this.id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getStatDate() { return this.statDate; }
    public void setStatDate(LocalDate statDate) { this.statDate = statDate; }
    public Integer getSessionCount() { return this.sessionCount; }
    public void setSessionCount(Integer sessionCount) { this.sessionCount = sessionCount; }
    public Integer getVisitPv() { return this.visitPv; }
    public void setVisitPv(Integer visitPv) { this.visitPv = visitPv; }
    public Integer getVisitUv() { return this.visitUv; }
    public void setVisitUv(Integer visitUv) { this.visitUv = visitUv; }
    public Integer getVisitUvNew() { return this.visitUvNew; }
    public void setVisitUvNew(Integer visitUvNew) { this.visitUvNew = visitUvNew; }
    public BigDecimal getStayTimeUv() { return this.stayTimeUv; }
    public void setStayTimeUv(BigDecimal stayTimeUv) { this.stayTimeUv = stayTimeUv; }
    public BigDecimal getStayTimeSession() { return this.stayTimeSession; }
    public void setStayTimeSession(BigDecimal stayTimeSession) { this.stayTimeSession = stayTimeSession; }
    public BigDecimal getVisitDepth() { return this.visitDepth; }
    public void setVisitDepth(BigDecimal visitDepth) { this.visitDepth = visitDepth; }
    public Integer getSyncStatus() { return this.syncStatus; }
    public void setSyncStatus(Integer syncStatus) { this.syncStatus = syncStatus; }
    public LocalDateTime getSyncedAt() { return this.syncedAt; }
    public void setSyncedAt(LocalDateTime syncedAt) { this.syncedAt = syncedAt; }
}
