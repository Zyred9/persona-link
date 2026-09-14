package com.personalink.server.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
/** t_feedback 用户反馈；请求号按用户隔离。 @author persona-link @since 2026-09-09 */
@Getter @Setter @TableName("t_feedback")
public class FeedbackEntity extends BaseAssessmentEntity {
    /** 用户标识；匿名提交为空。 */ private String openId;
    /** 提交时昵称快照；匿名或未设置时为空。 */ private String nickname;
    /** 客户端幂等请求号。 */ private String requestId;
    /** 用户提交的纯文本。 */ private String content;
}
