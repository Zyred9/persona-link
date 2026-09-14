package com.personalink.server.service.impl;

import com.personalink.server.service.AnalyticsService;
import com.personalink.server.service.BusinessSessionService;
import com.personalink.server.service.FeedbackService;
import com.personalink.server.service.MiniappAccountService;
import com.personalink.server.service.MiniappUserService;
import com.personalink.server.service.TestRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 账号注销编排：清空测试记录、反馈与埋点，失效全部会话，最后抹除账号资料。
 * <p>删除顺序保证注销过程中迟到的业务请求无法再关联到已注销账号。
 * @author persona-link
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class MiniappAccountServiceImpl implements MiniappAccountService {

    private final TestRecordService testRecordService;
    private final FeedbackService feedbackService;
    private final AnalyticsService analyticsService;
    private final BusinessSessionService businessSessionService;
    private final MiniappUserService miniappUserService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(String openId) {
        this.testRecordService.deleteAll(openId);
        this.feedbackService.deleteByOpenId(openId);
        this.analyticsService.deleteByOpenId(openId);
        this.businessSessionService.revokeMiniappSessions(openId, LocalDateTime.now());
        this.miniappUserService.anonymize(openId);
    }
}
