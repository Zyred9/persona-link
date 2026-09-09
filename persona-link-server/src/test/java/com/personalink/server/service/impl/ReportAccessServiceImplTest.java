package com.personalink.server.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.personalink.server.dto.AdResultRequest;
import com.personalink.server.entity.*;
import com.personalink.server.mapper.*;
import com.personalink.server.service.AdConfigService;
import com.personalink.server.exception.BusinessException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportAccessServiceImplTest {
    private ReportAccessServiceImpl service;
    private ReportAccessMapper grants;
    private ReportAdTaskMapper tasks;
    private AdConfigService configs;
    private ReportMapper reports;
    private AnswerSessionMapper answers;
    private PairSessionMapper pairs;
    private PairReportMapper pairReports;
    private AdConfigEntity config;
    private boolean granted;
    private long failureCount;

    @BeforeEach
    void setup() {
        for (Class<?> type : new Class<?>[]{ReportAccessEntity.class, ReportAdTaskEntity.class, PairReportEntity.class, AnswerSessionEntity.class, PairSessionEntity.class}) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "ad-test"), type);
        }
        grants = mock(ReportAccessMapper.class);
        tasks = mock(ReportAdTaskMapper.class);
        configs = mock(AdConfigService.class);
        reports = mock(ReportMapper.class);
        answers = mock(AnswerSessionMapper.class);
        pairs = mock(PairSessionMapper.class);
        pairReports = mock(PairReportMapper.class);
        service = new ReportAccessServiceImpl(configs, tasks, reports, answers, pairs, pairReports);
        ReflectionTestUtils.setField(service, "baseMapper", grants);
        ReflectionTestUtils.setField(service, "entityClass", ReportAccessEntity.class);
        ReportEntity report = new ReportEntity();
        report.setAnswerSessionId(10L);
        when(reports.selectById(7L)).thenReturn(report);
        AnswerSessionEntity answer = new AnswerSessionEntity();
        answer.setOpenId("owner");
        when(answers.selectOne(any(), eq(false))).thenReturn(answer);
        config = new AdConfigEntity();
        config.setEnabled(true);
        config.setAdUnitId("adunit-test");
        config.setFailurePolicy(1);
        when(configs.lockCurrent()).thenReturn(config);
        when(configs.current()).thenReturn(config);
        when(tasks.selectCount(any())).thenReturn(0L);
        when(grants.selectCount(any())).thenAnswer(invocation ->
                ((Wrapper<?>) invocation.getArgument(0)).getSqlSegment().contains("grant_source")
                        ? failureCount : granted ? 1L : 0L);
    }

    @Test
    void disabledGrantsOnceAndEnabledDoesNotRelock() {
        config.setEnabled(false);
        assertEquals(1, service.access("owner", 1, 7L).status());
        ArgumentCaptor<ReportAccessEntity> capture = ArgumentCaptor.forClass(ReportAccessEntity.class);
        verify(grants).insertIgnore(capture.capture());
        assertEquals(1, capture.getValue().getGrantSource());
        config.setEnabled(true);
        granted = true;
        assertEquals(1, service.access("owner", 1, 7L).status());
        verifyNoInteractions(tasks);
    }

    @Test
    void blocksWrongOwnerAndDirectDetail() {
        assertThrows(BusinessException.class, () -> service.access("other", 1, 7L));
        assertThrows(BusinessException.class, () -> service.requireRead("owner", 1, 7L));
        verify(grants, never()).insertIgnore(any());
    }

    @Test
    void createsTaskAndEnforcesHourlyLimit() {
        assertEquals(2, service.access("owner", 1, 7L).status());
        ArgumentCaptor<ReportAdTaskEntity> capture = ArgumentCaptor.forClass(ReportAdTaskEntity.class);
        verify(tasks).insert(capture.capture());
        assertEquals("owner", capture.getValue().getOpenId());
        assertTrue(capture.getValue().getExpiresAt().isAfter(LocalDateTime.now().plusMinutes(9)));
        when(tasks.selectCount(any())).thenReturn(20L);
        assertThrows(BusinessException.class, () -> service.access("owner", 1, 7L));
    }

    @Test
    void expiredOrChangedAdCannotUnlock() {
        ReportAdTaskEntity task = this.task();
        task.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        assertThrows(BusinessException.class, () -> service.submitAd("owner", 1, 7L, this.completed()));
        task.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        task.setAdUnitId("adunit-old");
        assertThrows(BusinessException.class, () -> service.submitAd("owner", 1, 7L, this.completed()));
        verify(grants, never()).insertIgnore(any());
    }

    @Test
    void completionIsIdempotentAndFailureQuotaDoesNotGrantFourth() {
        this.task();
        assertEquals(1, service.submitAd("owner", 1, 7L, this.completed()).status());
        granted = true;
        assertEquals(1, service.submitAd("owner", 1, 7L, this.completed()).status());
        verify(grants, times(1)).insertIgnore(any());
        granted = false;
        this.task();
        failureCount = 3L;
        assertEquals(2, service.submitAd("owner", 1, 7L, new AdResultRequest(
                "00000000-0000-0000-0000-000000000001", 2, 1004)).status());
        verify(grants, times(1)).insertIgnore(any());
    }

    @Test
    void failurePoliciesAndConsumedTask() {
        ReportAdTaskEntity task = this.task();
        assertEquals(1, service.submitAd("owner", 1, 7L, new AdResultRequest(
                "00000000-0000-0000-0000-000000000001", 2, 1004)).status());
        assertNotNull(task.getConsumedAt());
        this.task();
        config.setFailurePolicy(2);
        assertEquals(2, service.submitAd("owner", 1, 7L, new AdResultRequest(
                "00000000-0000-0000-0000-000000000001", 2, 1004)).status());
        verify(grants, times(1)).insertIgnore(any());
    }

    @Test
    void pairGrantIsForCurrentParticipantOnly() {
        PairSessionEntity pair = new PairSessionEntity();
        pair.setInitiatorOpenId("owner");
        pair.setPartnerOpenId("partner");
        pair.setInitiatorVisibleFlag(1);
        pair.setPartnerVisibleFlag(1);
        when(pairs.selectOne(any(), eq(false))).thenReturn(pair);
        when(pairReports.selectCount(any())).thenReturn(1L);
        config.setEnabled(false);
        assertEquals(1, service.access("partner", 2, 9L).status());
        ArgumentCaptor<ReportAccessEntity> capture = ArgumentCaptor.forClass(ReportAccessEntity.class);
        verify(grants).insertIgnore(capture.capture());
        assertEquals("partner", capture.getValue().getOpenId());
        assertEquals(2, capture.getValue().getReportType());
        assertThrows(BusinessException.class, () -> service.access("stranger", 2, 9L));
        pair.setPartnerVisibleFlag(0);
        assertThrows(BusinessException.class, () -> service.access("partner", 2, 9L));
    }

    private ReportAdTaskEntity task() {
        ReportAdTaskEntity task = new ReportAdTaskEntity();
        task.setId(1L);
        task.setAdUnitId("adunit-test");
        task.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(tasks.selectOne(any(), eq(false))).thenReturn(task);
        return task;
    }

    private AdResultRequest completed() {
        return new AdResultRequest("00000000-0000-0000-0000-000000000001", 1, null);
    }

    @Test
    void closingAdAllowsOldTaskAndExistingTaskIsReused() {
        ReportAdTaskEntity task = this.task();
        task.setTaskId("00000000-0000-0000-0000-000000000001");
        assertEquals(task.getTaskId(), service.access("owner", 1, 7L).taskId());
        verify(tasks, never()).insert(any(ReportAdTaskEntity.class));
        config.setEnabled(false);
        task.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        assertEquals(1, service.submitAd("owner", 1, 7L, this.completed()).status());
    }

    @Test
    void quotasUseReadCommittedAfterConfigLock() throws Exception {
        for (String method : new String[]{"access", "submitAd"}) {
            var types = method.equals("access")
                    ? new Class<?>[]{String.class, int.class, Long.class}
                    : new Class<?>[]{String.class, int.class, Long.class, AdResultRequest.class};
            var transaction = ReportAccessServiceImpl.class.getMethod(method, types)
                    .getAnnotation(org.springframework.transaction.annotation.Transactional.class);
            assertEquals(org.springframework.transaction.annotation.Isolation.READ_COMMITTED, transaction.isolation());
        }
    }
}
