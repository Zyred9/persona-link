package com.personalink.server.service;
import com.baomidou.mybatisplus.spring.service.IService;
import com.personalink.server.dto.*;
import com.personalink.server.entity.FeedbackEntity;
/** 用户反馈收件服务。 */
public interface FeedbackService extends IService<FeedbackEntity> {
    FeedbackResponse submit(String openId, FeedbackCreateRequest request);
    PageResponse<AdminFeedbackResponse> history(long page, long size);
}
