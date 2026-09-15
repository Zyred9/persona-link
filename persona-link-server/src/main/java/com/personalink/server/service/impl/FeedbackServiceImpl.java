package com.personalink.server.service.impl;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.personalink.server.dto.*;
import com.personalink.server.entity.FeedbackEntity;
import com.personalink.server.entity.MiniappUserEntity;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.mapper.FeedbackMapper;
import com.personalink.server.miniapp.security.WechatContentSecurityClient;
import com.personalink.server.service.FeedbackService;
import com.personalink.server.service.MiniappUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import java.util.Objects;
/** 反馈持久化，必须登录提交：数据库唯一键保证断网重试不会重复收件。 */
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl extends ServiceImpl<FeedbackMapper, FeedbackEntity> implements FeedbackService {
    private final WechatContentSecurityClient contentSecurityClient;
    private final MiniappUserService miniappUserService;
    @Override
    @Transactional(rollbackFor=Exception.class)
    public FeedbackResponse submit(String openId, FeedbackCreateRequest request) {
        Assert.hasText(openId, "反馈必须登录后提交");
        if (!this.contentSecurityClient.isTextAllowed(openId, request.content(),
                WechatContentSecurityClient.SCENE_COMMENT)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40005, "反馈内容含违规信息，请修改后重新提交");
        }
        FeedbackEntity entity = new FeedbackEntity();
        entity.setOpenId(openId);
        entity.setNickname(this.nicknameOf(openId));
        entity.setRequestId(request.requestId());
        entity.setContent(request.content());
        this.baseMapper.insertIdempotent(entity);
        FeedbackEntity saved = this.lambdaQuery().eq(FeedbackEntity::getOpenId, openId)
                .eq(FeedbackEntity::getRequestId, request.requestId()).eq(FeedbackEntity::getDeleted, 0)
                .last("FOR UPDATE").one();
        if (Objects.isNull(saved) || !Objects.equals(saved.getContent(), request.content())) {
            throw new BusinessException(HttpStatus.CONFLICT, 409, "反馈请求号已用于其他内容，请重新提交");
        }
        return this.response(saved);
    }
    @Override
    public PageResponse<AdminFeedbackResponse> history(long page, long size) {
        long total = this.lambdaQuery().eq(FeedbackEntity::getDeleted, 0).count();
        var records = this.lambdaQuery().eq(FeedbackEntity::getDeleted, 0)
                .orderByDesc(FeedbackEntity::getCreateDate).orderByDesc(FeedbackEntity::getId)
                .last("LIMIT " + size + " OFFSET " + Math.multiplyExact(page - 1, size)).list();
        return new PageResponse<>(records.stream().map(entity -> new AdminFeedbackResponse(
                String.valueOf(entity.getId()), entity.getOpenId(), entity.getNickname(),
                entity.getContent(), entity.getCreateDate()))
                .toList(), total, page, size);
    }

    @Override
    @Transactional(rollbackFor=Exception.class)
    public void deleteByOpenId(String openId) {
        Assert.hasText(openId, "反馈归属用户不能为空");
        this.lambdaUpdate().set(FeedbackEntity::getDeleted, 1)
                .eq(FeedbackEntity::getOpenId, openId).eq(FeedbackEntity::getDeleted, 0).update();
    }
    /** 提交时记录用户昵称快照；用户不存在时为空。 */
    private String nicknameOf(String openId) {
        MiniappUserEntity user = this.miniappUserService.findByOpenId(openId);
        return Objects.isNull(user) || Objects.isNull(user.getNickname()) ? "" : user.getNickname();
    }
    private FeedbackResponse response(FeedbackEntity entity) {
        return new FeedbackResponse(String.valueOf(entity.getId()), entity.getContent(), entity.getCreateDate());
    }
}
