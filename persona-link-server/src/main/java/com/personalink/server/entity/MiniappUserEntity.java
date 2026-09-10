package com.personalink.server.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 小程序用户资料表 t_miniapp_user。
 * <p>OpenID 唯一，用户身份仅来自已校验的业务会话。
 * @author persona-link
 * @since 1.0.0
 */
@Getter
@Setter
@TableName("t_miniapp_user")
public class MiniappUserEntity extends BaseAssessmentEntity {
    /** 微信用户 OpenID。 */
    private String openId;
    /** 用户主动填写的昵称，未完善时为空串。 */
    private String nickname;
    /** 本服务上传的头像地址，未完善时为空串。 */
    private String avatarUrl;
}
