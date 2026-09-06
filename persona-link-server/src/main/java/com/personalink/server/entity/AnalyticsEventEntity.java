package com.personalink.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 小程序访问事件实体，对应 t_analytics_event。
 *
 * @author persona-link
 * @since 1.0.0
 */
@TableName("t_analytics_event")
public class AnalyticsEventEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private String openId;
    private String sessionId;
    private Integer eventType;
    private String pagePath;
    private Long businessId;
    private Integer sourceScene;
    private String channel;
    private String appVersion;
    private LocalDateTime clientTime;
    private LocalDateTime receivedTime;

    public Long getId() { return this.id; }
    public void setId(Long id) { this.id = id; }
    public String getEventId() { return this.eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getOpenId() { return this.openId; }
    public void setOpenId(String openId) { this.openId = openId; }
    public String getSessionId() { return this.sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public Integer getEventType() { return this.eventType; }
    public void setEventType(Integer eventType) { this.eventType = eventType; }
    public String getPagePath() { return this.pagePath; }
    public void setPagePath(String pagePath) { this.pagePath = pagePath; }
    public Long getBusinessId() { return this.businessId; }
    public void setBusinessId(Long businessId) { this.businessId = businessId; }
    public Integer getSourceScene() { return this.sourceScene; }
    public void setSourceScene(Integer sourceScene) { this.sourceScene = sourceScene; }
    public String getChannel() { return this.channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getAppVersion() { return this.appVersion; }
    public void setAppVersion(String appVersion) { this.appVersion = appVersion; }
    public LocalDateTime getClientTime() { return this.clientTime; }
    public void setClientTime(LocalDateTime clientTime) { this.clientTime = clientTime; }
    public LocalDateTime getReceivedTime() { return this.receivedTime; }
    public void setReceivedTime(LocalDateTime receivedTime) { this.receivedTime = receivedTime; }
}
