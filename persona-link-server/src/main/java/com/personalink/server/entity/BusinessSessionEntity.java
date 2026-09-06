package com.personalink.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 持久化业务会话实体，对应 t_business_session。
 *
 * <p>仅保存令牌 SHA-256 哈希，后台会话与小程序会话主体互斥。</p>
 *
 * @author persona-link
 * @since 1.0.0
 */
@TableName("t_business_session")
public class BusinessSessionEntity {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 会话令牌 SHA-256 哈希。 */
    private String sessionTokenHash;
    /** 会话类型：1后台，2微信小程序。 */
    private Integer sessionType;
    /** 后台账号 ID。 */
    private Long adminAccountId;
    /** 微信用户 OpenID。 */
    private String openId;
    /** 过期时间。 */
    private LocalDateTime expiresAt;
    /** 最后访问时间。 */
    private LocalDateTime lastAccessAt;
    /** 注销时间。 */
    private LocalDateTime revokedAt;
    /** 删除标记：0正常，1删除。 */
    @TableLogic
    private Integer deleted;
    /** 创建时间。 */
    private LocalDateTime createDate;
    /** 更新时间。 */
    private LocalDateTime updateDate;

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionTokenHash() {
        return this.sessionTokenHash;
    }

    public void setSessionTokenHash(String sessionTokenHash) {
        this.sessionTokenHash = sessionTokenHash;
    }

    public Integer getSessionType() {
        return this.sessionType;
    }

    public void setSessionType(Integer sessionType) {
        this.sessionType = sessionType;
    }

    public Long getAdminAccountId() {
        return this.adminAccountId;
    }

    public void setAdminAccountId(Long adminAccountId) {
        this.adminAccountId = adminAccountId;
    }

    public String getOpenId() {
        return this.openId;
    }

    public void setOpenId(String openId) {
        this.openId = openId;
    }

    public LocalDateTime getExpiresAt() {
        return this.expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getLastAccessAt() {
        return this.lastAccessAt;
    }

    public void setLastAccessAt(LocalDateTime lastAccessAt) {
        this.lastAccessAt = lastAccessAt;
    }

    public LocalDateTime getRevokedAt() {
        return this.revokedAt;
    }

    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public Integer getDeleted() {
        return this.deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getCreateDate() {
        return this.createDate;
    }

    public void setCreateDate(LocalDateTime createDate) {
        this.createDate = createDate;
    }

    public LocalDateTime getUpdateDate() {
        return this.updateDate;
    }

    public void setUpdateDate(LocalDateTime updateDate) {
        this.updateDate = updateDate;
    }
}
