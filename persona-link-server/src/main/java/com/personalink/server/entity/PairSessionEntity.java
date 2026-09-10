package com.personalink.server.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 双人配对会话实体，对应 t_pair_session。
 */
@TableName("t_pair_session")
public class PairSessionEntity extends BaseAssessmentEntity {

    /** 配对业务编号。 */
    private String pairNo;
    /** 创建邀请幂等请求号。 */
    private String createRequestId;
    /** 邀请令牌 SHA-256。 */
    private String inviteTokenHash;
    /** 双方共同使用的题型版本 ID。 */
    private Long versionId;
    /** 配对创建时的题型封面快照。 */
    private String coverUrl;
    /** 发起者 OpenID。 */
    private String initiatorOpenId;
    /** 发起者答题会话 ID。 */
    private Long initiatorAnswerSessionId;
    /** 受邀者 OpenID。 */
    private String partnerOpenId;
    /** 受邀者答题会话 ID。 */
    private Long partnerAnswerSessionId;
    /** 配对状态。 */
    private Integer pairStatus;
    /** 发起者可见标记。 */
    private Integer initiatorVisibleFlag;
    /** 受邀者可见标记。 */
    private Integer partnerVisibleFlag;
    /** 加入时间。 */
    private LocalDateTime joinedAt;
    /** 邀请过期时间。 */
    private LocalDateTime expiresAt;

    /** 本人删除记录后，旧 ID 和邀请码也不能重新取得本人访问权。 */
    public boolean isVisibleTo(String openId) {
        return java.util.Objects.nonNull(openId)
                && ((openId.equals(this.initiatorOpenId) && Integer.valueOf(1).equals(this.initiatorVisibleFlag))
                || (openId.equals(this.partnerOpenId) && Integer.valueOf(1).equals(this.partnerVisibleFlag)));
    }

    public String getPairNo() { return this.pairNo; }
    public void setPairNo(String pairNo) { this.pairNo = pairNo; }
    public String getCreateRequestId() { return this.createRequestId; }
    public void setCreateRequestId(String createRequestId) { this.createRequestId = createRequestId; }
    public String getInviteTokenHash() { return this.inviteTokenHash; }
    public void setInviteTokenHash(String inviteTokenHash) { this.inviteTokenHash = inviteTokenHash; }
    public Long getVersionId() { return this.versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public String getCoverUrl() { return this.coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public String getInitiatorOpenId() { return this.initiatorOpenId; }
    public void setInitiatorOpenId(String initiatorOpenId) { this.initiatorOpenId = initiatorOpenId; }
    public Long getInitiatorAnswerSessionId() { return this.initiatorAnswerSessionId; }
    public void setInitiatorAnswerSessionId(Long initiatorAnswerSessionId) { this.initiatorAnswerSessionId = initiatorAnswerSessionId; }
    public String getPartnerOpenId() { return this.partnerOpenId; }
    public void setPartnerOpenId(String partnerOpenId) { this.partnerOpenId = partnerOpenId; }
    public Long getPartnerAnswerSessionId() { return this.partnerAnswerSessionId; }
    public void setPartnerAnswerSessionId(Long partnerAnswerSessionId) { this.partnerAnswerSessionId = partnerAnswerSessionId; }
    public Integer getPairStatus() { return this.pairStatus; }
    public void setPairStatus(Integer pairStatus) { this.pairStatus = pairStatus; }
    public Integer getInitiatorVisibleFlag() { return this.initiatorVisibleFlag; }
    public void setInitiatorVisibleFlag(Integer initiatorVisibleFlag) { this.initiatorVisibleFlag = initiatorVisibleFlag; }
    public Integer getPartnerVisibleFlag() { return this.partnerVisibleFlag; }
    public void setPartnerVisibleFlag(Integer partnerVisibleFlag) { this.partnerVisibleFlag = partnerVisibleFlag; }
    public LocalDateTime getJoinedAt() { return this.joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
    public LocalDateTime getExpiresAt() { return this.expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
