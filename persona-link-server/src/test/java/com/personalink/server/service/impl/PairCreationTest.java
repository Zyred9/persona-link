package com.personalink.server.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.dto.CreatePairRequest;
import com.personalink.server.entity.*;
import com.personalink.server.enums.AnswerStatus;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.mapper.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PairCreationTest {
    private final AnswerSessionMapper answers = mock(AnswerSessionMapper.class);
    private final TestVersionMapper versions = mock(TestVersionMapper.class);
    private final TestMapper tests = mock(TestMapper.class);
    private final ReportMapper reports = mock(ReportMapper.class);
    private final PairSessionMapper pairs = mock(PairSessionMapper.class);
    private final PairReportMapper pairReports = mock(PairReportMapper.class);
    private final CreatePairRequest request = new CreatePairRequest("10", "pair-create-request");
    private PairAssessmentServiceImpl service;
    private PairSessionEntity created;

    @BeforeEach
    void setup() {
        for (Class<?> type : new Class<?>[]{AnswerSessionEntity.class, ReportEntity.class,
                PairSessionEntity.class, PairReportEntity.class}) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "pair-create-test"), type);
        }
        this.service = new PairAssessmentServiceImpl(this.answers, null, this.versions, this.tests,
                this.pairReports, this.reports, new ObjectMapper(), null);
        ReflectionTestUtils.setField(this.service, "baseMapper", this.pairs);
        ReflectionTestUtils.setField(this.service, "entityClass", PairSessionEntity.class);
        AnswerSessionEntity answer = new AnswerSessionEntity();
        answer.setId(10L);
        answer.setOpenId("owner");
        answer.setVersionId(20L);
        answer.setAnswerStatus(AnswerStatus.REPORT_READY.getCode());
        when(this.answers.selectOne(any(), eq(false))).thenReturn(answer);
        TestVersionEntity version = new TestVersionEntity();
        version.setTestId(30L);
        when(this.versions.selectById(20L)).thenReturn(version);
        TestEntity test = new TestEntity();
        test.setTestType(2);
        when(this.tests.selectById(30L)).thenReturn(test);
        when(this.pairs.selectOne(any(), eq(false))).thenAnswer(invocation -> this.created);
        when(this.pairs.insertIgnore(any())).thenAnswer(invocation -> {
            this.created = invocation.getArgument(0);
            this.created.setId(60L);
            return 1;
        });
    }

    @Test
    void deletedReportCannotCreateAnInvitation() {
        assertThrows(BusinessException.class, () -> this.service.create("owner", this.request));
        verifyNoInteractions(this.pairs);
        verify(this.reports).selectOne(argThat(query -> query.getSqlSegment().contains("deleted")
                && query.getSqlSegment().contains("FOR UPDATE")), eq(false));
    }

    @Test
    void ownerWithLiveReportCanRetryWithoutCreatingAnotherPair() {
        when(this.reports.selectOne(any(), eq(false))).thenReturn(new ReportEntity());
        var first = this.service.create("owner", this.request);
        var retried = this.service.create("owner", this.request);
        assertEquals(first.inviteToken(), retried.inviteToken());
        verify(this.pairs, times(1)).insertIgnore(any());
        when(this.reports.selectOne(any(), eq(false))).thenReturn(null);
        assertThrows(BusinessException.class, () -> this.service.create("owner", this.request));
        verify(this.pairs, times(1)).insertIgnore(any());
    }

    @Test
    void foreignUserCannotCheckOrUseAnotherUsersReport() {
        assertThrows(BusinessException.class, () -> this.service.create("stranger", this.request));
        verifyNoInteractions(this.reports, this.pairs);
    }
}
