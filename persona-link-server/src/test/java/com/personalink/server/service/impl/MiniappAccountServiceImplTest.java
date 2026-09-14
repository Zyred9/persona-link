package com.personalink.server.service.impl;

import com.personalink.server.service.AnalyticsService;
import com.personalink.server.service.BusinessSessionService;
import com.personalink.server.service.FeedbackService;
import com.personalink.server.service.MiniappUserService;
import com.personalink.server.service.TestRecordService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class MiniappAccountServiceImplTest {

    @Test
    void cancelErasesPersonalDataAndRevokesSessionsInOrder() {
        TestRecordService testRecordService = mock(TestRecordService.class);
        FeedbackService feedbackService = mock(FeedbackService.class);
        AnalyticsService analyticsService = mock(AnalyticsService.class);
        BusinessSessionService businessSessionService = mock(BusinessSessionService.class);
        MiniappUserService miniappUserService = mock(MiniappUserService.class);
        MiniappAccountServiceImpl service = new MiniappAccountServiceImpl(testRecordService,
                feedbackService, analyticsService, businessSessionService, miniappUserService);

        service.cancel("openid-a");

        InOrder order = inOrder(testRecordService, feedbackService, analyticsService,
                businessSessionService, miniappUserService);
        order.verify(testRecordService).deleteAll("openid-a");
        order.verify(feedbackService).deleteByOpenId("openid-a");
        order.verify(analyticsService).deleteByOpenId("openid-a");
        order.verify(businessSessionService).revokeMiniappSessions(eq("openid-a"), any(LocalDateTime.class));
        order.verify(miniappUserService).anonymize("openid-a");
    }
}
