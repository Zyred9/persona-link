package com.personalink.server.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 微信头像审核结果，按 traceId 保留首次结论。
 * <p>独立于用户待审记录保存，支持回调先于上传请求落库；不保存头像或用户身份。
 * @author persona-link
 * @since 2026-09-14
 */
@Getter
@Setter
@TableName("t_avatar_audit_result")
public class AvatarAuditResultEntity extends BaseAssessmentEntity {
    /** 微信审核任务号，唯一。 */
    private String traceId;
    /** 是否明确审核通过。 */
    private Boolean passed;
}
