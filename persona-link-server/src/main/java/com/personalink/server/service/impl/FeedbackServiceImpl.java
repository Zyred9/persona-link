package com.personalink.server.service.impl;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.personalink.server.dto.*;
import com.personalink.server.entity.FeedbackEntity;
import com.personalink.server.mapper.FeedbackMapper;
import com.personalink.server.service.FeedbackService;
import com.personalink.server.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
/** 反馈持久化，数据库唯一键保证断网重试不会重复收件。 */
@Service
public class FeedbackServiceImpl extends ServiceImpl<FeedbackMapper, FeedbackEntity> implements FeedbackService {
    @Override
    @Transactional(rollbackFor=Exception.class)
    public FeedbackResponse submit(String openId, FeedbackCreateRequest request) {
        FeedbackEntity entity = new FeedbackEntity();
        entity.setOpenId(openId);
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
                String.valueOf(entity.getId()), entity.getOpenId(), entity.getContent(), entity.getCreateDate()))
                .toList(), total, page, size);
    }
    private FeedbackResponse response(FeedbackEntity entity) {
        return new FeedbackResponse(String.valueOf(entity.getId()), entity.getContent(), entity.getCreateDate());
    }
}
