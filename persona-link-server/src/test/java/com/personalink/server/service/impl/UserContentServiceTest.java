package com.personalink.server.service.impl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.personalink.server.dto.*;
import com.personalink.server.entity.*;
import com.personalink.server.mapper.*;
import com.personalink.server.miniapp.security.WechatContentSecurityClient;
import com.personalink.server.service.MiniappUserService;
import com.personalink.server.exception.BusinessException;
import jakarta.validation.Validation;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserContentServiceTest {
    private void initialize(Object service, Object mapper, Class<?> entity) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "content-test"), entity);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        ReflectionTestUtils.setField(service, "entityClass", entity);
    }
    @Test
    void requestValidationRejectsBlankAndOversizeText() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(new FeedbackCreateRequest("key", "   ")).isEmpty());
            assertFalse(validator.validate(new FeedbackCreateRequest("key", "x".repeat(501))).isEmpty());
            assertFalse(validator.validate(new LegalDocumentSaveRequest("title", " ")).isEmpty());
            assertTrue(validator.validate(new FeedbackCreateRequest(" key ", " content ")).isEmpty());
            assertEquals("content", new FeedbackCreateRequest("key", " content ").content());
        }
    }
    @Test
    void feedbackRetryReturnsSameRecordButChangedContentConflicts() {
        var mapper = mock(FeedbackMapper.class);
        var security = mock(WechatContentSecurityClient.class);
        when(security.isTextAllowed(any(), any(), anyInt())).thenReturn(true);
        var service = new FeedbackServiceImpl(security, mock(MiniappUserService.class));
        this.initialize(service, mapper, FeedbackEntity.class);
        var saved = new FeedbackEntity();
        saved.setId(1L); saved.setContent("feedback"); saved.setCreateDate(LocalDateTime.now());
        when(mapper.selectOne(any())).thenReturn(saved);
        var request = new FeedbackCreateRequest("key", "feedback");
        assertEquals("1", service.submit("owner", request).id());
        assertEquals("1", service.submit("owner", request).id());
        assertThrows(BusinessException.class, () -> service.submit("owner", new FeedbackCreateRequest("key", "changed")));
        verify(mapper, times(3)).insertIdempotent(argThat(entity -> "owner".equals(entity.getOpenId())));
    }
    @Test
    void feedbackRiskyContentIsRejectedBeforeInsert() {
        var mapper = mock(FeedbackMapper.class);
        var security = mock(WechatContentSecurityClient.class);
        when(security.isTextAllowed("owner", "违规内容", WechatContentSecurityClient.SCENE_COMMENT))
                .thenReturn(false);
        var service = new FeedbackServiceImpl(security, mock(MiniappUserService.class));

        assertThrows(BusinessException.class, () -> service.submit("owner", new FeedbackCreateRequest("key", "违规内容")));
        verify(mapper, never()).insertIdempotent(any());
    }
    @Test
    void feedbackRejectsMissingLoginIdentity() {
        var mapper = mock(FeedbackMapper.class);
        var security = mock(WechatContentSecurityClient.class);
        var service = new FeedbackServiceImpl(security, mock(MiniappUserService.class));
        this.initialize(service, mapper, FeedbackEntity.class);

        assertThrows(IllegalArgumentException.class, () -> service.submit(null, new FeedbackCreateRequest("key", "反馈")));
        assertThrows(IllegalArgumentException.class, () -> service.submit(" ", new FeedbackCreateRequest("key", "反馈")));

        verify(security, never()).isTextAllowed(any(), any(), anyInt());
        verify(mapper, never()).insertIdempotent(any());
    }
    @Test
    void feedbackDeletionRequiresAccountIdentity() {
        var mapper = mock(FeedbackMapper.class);
        var service = new FeedbackServiceImpl(mock(WechatContentSecurityClient.class), mock(MiniappUserService.class));
        this.initialize(service, mapper, FeedbackEntity.class);

        assertThrows(IllegalArgumentException.class, () -> service.deleteByOpenId(" "));

        verifyNoInteractions(mapper);
    }
    @Test
    void authenticatedFeedbackSavesSubmitterNicknameSnapshot() {
        var mapper = mock(FeedbackMapper.class);
        var security = mock(WechatContentSecurityClient.class);
        var users = mock(MiniappUserService.class);
        when(security.isTextAllowed(any(), any(), anyInt())).thenReturn(true);
        var owner = new MiniappUserEntity();
        owner.setNickname("小明");
        when(users.findByOpenId("owner")).thenReturn(owner);
        var service = new FeedbackServiceImpl(security, users);
        this.initialize(service, mapper, FeedbackEntity.class);
        var saved = new FeedbackEntity();
        saved.setId(3L); saved.setContent("反馈"); saved.setCreateDate(LocalDateTime.now());
        when(mapper.selectOne(any())).thenReturn(saved);

        service.submit("owner", new FeedbackCreateRequest("key", "反馈"));

        verify(mapper).insertIdempotent(argThat(entity -> "owner".equals(entity.getOpenId()) && "小明".equals(entity.getNickname())));
    }
    @Test
    void feedbackHistoryIncludesAuthenticatedSubmitter() {
        var mapper = mock(FeedbackMapper.class);
        var service = new FeedbackServiceImpl(mock(WechatContentSecurityClient.class), mock(MiniappUserService.class));
        this.initialize(service, mapper, FeedbackEntity.class);
        var saved = new FeedbackEntity();
        saved.setId(7L);
        saved.setOpenId("feedback-owner");
        saved.setNickname("小明");
        saved.setContent("feedback");
        saved.setCreateDate(LocalDateTime.now());
        when(mapper.selectCount(any())).thenReturn(1L);
        when(mapper.selectList(any())).thenReturn(List.of(saved));

        var page = service.history(1, 20);

        assertEquals(1, page.total());
        assertEquals(new AdminFeedbackResponse("7", "feedback-owner", "小明", "feedback", saved.getCreateDate()),
                page.records().get(0));
    }
    @Test
    void unconfiguredLegalDocumentsHaveNoFakePublicContent() {
        var mapper = mock(LegalDocumentMapper.class);
        var service = new LegalDocumentServiceImpl();
        this.initialize(service, mapper, LegalDocumentEntity.class);
        when(mapper.selectList(any())).thenReturn(List.of());
        assertEquals(3, service.documents().size());
        assertTrue(service.documents().stream().allMatch(doc -> doc.content().isEmpty() && doc.version() == 0));
        assertThrows(BusinessException.class, () -> service.published(1));
        assertThrows(BusinessException.class, () -> service.published(4));
        verify(mapper, never()).publish(any());
    }

    @Test
    void legalVersionCheckReturnsVersionsWithoutLoadingDocumentBodies() {
        var mapper = mock(LegalDocumentMapper.class);
        var service = new LegalDocumentServiceImpl();
        this.initialize(service, mapper, LegalDocumentEntity.class);
        var saved = new LegalDocumentEntity();
        saved.setType(2);
        saved.setVersion(7L);
        when(mapper.selectList(any())).thenReturn(List.of(saved));

        assertEquals(List.of(new LegalDocumentVersionResponse(2, 7)), service.versions());
        verify(mapper).selectList(argThat(wrapper -> wrapper.getSqlSelect().contains("type")
                && wrapper.getSqlSelect().contains("version")
                && !wrapper.getSqlSelect().contains("content")));
        when(mapper.selectList(any())).thenReturn(List.of());
        assertTrue(service.versions().isEmpty());
    }
    @Test
    void privacyLocksBeforeDeletingAndUsesOnlyAuthenticatedOwner() {
        var mapper = mock(TestRecordMapper.class);
        var service = new TestRecordServiceImpl();
        this.initialize(service, mapper, AnswerSessionEntity.class);
        when(mapper.selectList(any())).thenReturn(List.of());
        service.deleteAll("owner");
        var order = inOrder(mapper);
        order.verify(mapper).selectList(argThat(wrapper -> wrapper.getSqlSegment().contains("FOR UPDATE")));
        order.verify(mapper).hidePairs("owner");
        order.verify(mapper).deleteAnswers("owner");
        order.verify(mapper).deleteSnapshots("owner");
        order.verify(mapper).deleteReports("owner");
        order.verify(mapper).deleteGrants("owner");
        order.verify(mapper).deleteAdTasks("owner");
        order.verify(mapper).update(isNull(), any());
    }
    @Test
    void deletedPairParticipantCannotReadButPartnerCan() {
        var pair = new PairSessionEntity();
        pair.setInitiatorOpenId("owner"); pair.setPartnerOpenId("partner");
        pair.setInitiatorVisibleFlag(0); pair.setPartnerVisibleFlag(1);
        assertFalse(pair.isVisibleTo("owner"));
        assertFalse(pair.isVisibleTo("stranger"));
        assertFalse(pair.isVisibleTo(null));
        assertTrue(pair.isVisibleTo("partner"));
    }
}
