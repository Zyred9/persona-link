package com.personalink.server.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.dto.DimensionScoreResponse;
import com.personalink.server.dto.RestartAssessmentRequest;
import com.personalink.server.dto.SaveAnswerRequest;
import com.personalink.server.entity.*;
import com.personalink.server.enums.AnswerStatus;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.mapper.*;
import jakarta.validation.Validation;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssessmentRecoveryTest {
    private final AnswerSessionMapper answers = mock(AnswerSessionMapper.class);
    private final TestVersionMapper versions = mock(TestVersionMapper.class);
    private final QuestionMapper questions = mock(QuestionMapper.class);
    private final QuestionOptionMapper options = mock(QuestionOptionMapper.class);
    private final AnswerSessionQuestionMapper snapshots = mock(AnswerSessionQuestionMapper.class);
    private final AnswerDetailMapper details = mock(AnswerDetailMapper.class);
    private final PairSessionMapper pairs = mock(PairSessionMapper.class);
    private final AnswerSessionEntity answer = new AnswerSessionEntity();
    private final QuestionEntity question = new QuestionEntity();
    private final AnswerSessionQuestionEntity snapshot = new AnswerSessionQuestionEntity();
    private AssessmentServiceImpl service;

    @BeforeEach
    void setup() {
        for (Class<?> type : new Class<?>[]{AnswerSessionEntity.class, PairSessionEntity.class,
                AnswerSessionQuestionEntity.class, AnswerDetailEntity.class, QuestionEntity.class,
                QuestionOptionEntity.class}) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "recovery-test"), type);
        }
        this.service = new AssessmentServiceImpl(null, this.versions, null, this.questions, this.options,
                null, this.snapshots, this.details, null, this.pairs, null, new ObjectMapper(), null);
        ReflectionTestUtils.setField(this.service, "baseMapper", this.answers);
        ReflectionTestUtils.setField(this.service, "entityClass", AnswerSessionEntity.class);
        this.answer.setId(10L);
        this.answer.setOpenId("owner");
        this.answer.setVersionId(20L);
        this.answer.setAnswerType(1);
        this.answer.setAnswerStatus(1);
        when(this.answers.selectById(10L)).thenReturn(this.answer);
        when(this.answers.selectOne(any())).thenReturn(this.answer);
        TestVersionEntity version = new TestVersionEntity();
        version.setId(20L);
        version.setTestId(30L);
        version.setTitle("测试");
        when(this.versions.selectById(20L)).thenReturn(version);
        this.question.setId(40L);
        this.question.setQuestionType(1);
        this.question.setRequiredFlag(0);
        this.question.setMinSelectCount(1);
        this.question.setMaxSelectCount(1);
        this.question.setDimensionId(50L);
        this.snapshot.setQuestionId(40L);
        when(this.snapshots.selectList(any())).thenReturn(List.of(this.snapshot));
        when(this.snapshots.selectCount(any())).thenReturn(1L);
        when(this.questions.selectById(40L)).thenReturn(this.question);
        when(this.questions.selectList(any())).thenReturn(List.of(this.question));
        when(this.options.selectList(any())).thenReturn(List.of());
        when(this.details.selectList(any())).thenReturn(List.of());
    }

    @Test
    void singleAndPairInitiatorResumeKeepTheirFlow() {
        var single = this.service.resume("owner", 10L);
        assertEquals(1, single.answerType());
        assertNull(single.pairSessionId());
        assertTrue(single.canRestart());
        assertEquals(0, single.completedCount());
        assertEquals(0, single.firstUnansweredIndex());
        verifyNoInteractions(this.pairs);
        this.answer.setAnswerType(2);
        var initiator = this.service.resume("owner", 10L);
        assertEquals(2, initiator.answerType());
        assertNull(initiator.pairSessionId());
        assertTrue(initiator.canRestart());
    }

    @Test
    void participantResumesOriginalPairAndCannotAbandonItsSnapshot() {
        this.answer.setAnswerType(2);
        PairSessionEntity pair = new PairSessionEntity();
        pair.setId(60L);
        when(this.pairs.selectOne(any(), eq(false))).thenReturn(pair);
        var response = this.service.resume("owner", 10L);
        assertEquals(2, response.answerType());
        assertEquals("60", response.pairSessionId());
        assertFalse(response.canRestart());
        assertThrows(BusinessException.class, () -> this.service.restart("owner", 10L,
                new RestartAssessmentRequest("restart-request")));
        verify(this.answers, never()).update(isNull(), any());
        verify(this.answers, never()).insertIgnore(any());
        verify(this.pairs, atLeastOnce()).selectOne(argThat(query -> {
            String sql = query.getSqlSegment();
            return sql.contains("partner_answer_session_id") && sql.contains("partner_open_id")
                    && sql.contains("deleted");
        }), eq(false));
    }

    @Test
    void foreignUserCannotReadOrRestartOrClearAnswers() {
        assertThrows(BusinessException.class, () -> this.service.resume("stranger", 10L));
        assertThrows(BusinessException.class, () -> this.service.restart("stranger", 10L,
                new RestartAssessmentRequest("restart-request")));
        assertThrows(BusinessException.class, () -> this.service.saveAnswer("stranger", 10L,
                new SaveAnswerRequest("40", List.of())));
        verifyNoInteractions(this.pairs, this.details, this.versions);
    }

    @Test
    void abandonOnlyChangesOwnedUnfinishedSessionAndCancelsItsPendingPair() {
        this.service.abandon("owner", 10L);
        verify(this.answers).selectOne(argThat(query -> query.getSqlSegment().contains("FOR UPDATE")));
        verify(this.answers).update(isNull(), argThat(query -> {
            String sql = query.getSqlSegment();
            return sql.contains("id") && sql.contains("answer_status") && sql.contains("deleted");
        }));
        verify(this.pairs).update(isNull(), argThat(query -> {
            String sql = query.getSqlSegment();
            return sql.contains("partner_answer_session_id") && sql.contains("partner_open_id")
                    && sql.contains("pair_status") && sql.contains("deleted");
        }));
        verifyNoInteractions(this.details, this.snapshots, this.versions);
        verify(this.answers, never()).insertIgnore(any());
    }

    @Test
    void abandonRetryIsIdempotentAndForeignOrCompletedSessionsAreRejected() {
        this.answer.setAnswerStatus(AnswerStatus.ABANDONED.getCode());
        this.service.abandon("owner", 10L);
        assertThrows(BusinessException.class, () -> this.service.abandon("stranger", 10L));
        this.answer.setAnswerStatus(AnswerStatus.REPORT_READY.getCode());
        assertThrows(BusinessException.class, () -> this.service.abandon("owner", 10L));
        verify(this.answers, never()).update(isNull(), any());
        verifyNoInteractions(this.pairs, this.details, this.snapshots);
    }

    @Test
    void optionalSingleAndMultipleCanBeClearedIdempotentlyWithoutEmptyInsert() {
        for (int type : List.of(1, 2)) {
            this.question.setQuestionType(type);
            for (int retry = 0; retry < 2; retry++) {
                this.service.saveAnswer("owner", 10L, new SaveAnswerRequest("40", List.of()));
            }
        }
        verify(this.details, times(4)).update(isNull(), any());
        verify(this.details, never()).upsertBatch(anyList());
        verifyNoInteractions(this.options);
    }

    @Test
    void requiredEmptyAndForeignOptionDoNotEraseStoredAnswers() {
        this.question.setRequiredFlag(1);
        assertThrows(BusinessException.class, () -> this.service.saveAnswer("owner", 10L,
                new SaveAnswerRequest("40", List.of())));
        assertThrows(BusinessException.class, () -> this.service.saveAnswer("owner", 10L,
                new SaveAnswerRequest("40", List.of("99"))));
        verifyNoInteractions(this.details);
    }

    @Test
    void selectingOptionalAnswerStillPersistsItsOption() {
        QuestionOptionEntity option = this.option(71L, 4);
        when(this.options.selectList(any())).thenReturn(List.of(option));
        this.service.saveAnswer("owner", 10L, new SaveAnswerRequest("40", List.of("71")));
        verify(this.details).upsertBatch(argThat(saved -> saved.size() == 1
                && Long.valueOf(10L).equals(saved.get(0).getAnswerSessionId())
                && Long.valueOf(71L).equals(saved.get(0).getOptionId())));
    }

    @Test
    void emptyListIsValidInputButMissingListIsNot() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(new SaveAnswerRequest("40", List.of())).isEmpty());
            assertFalse(validator.validate(new SaveAnswerRequest("40", null)).isEmpty());
        }
    }

    @Test
    void skippedOptionalQuestionCanSubmitAndKeepsZeroWithinScoreRange() {
        ScoreDimensionEntity dimension = new ScoreDimensionEntity();
        dimension.setId(50L);
        dimension.setDimensionCode("D");
        List<QuestionOptionEntity> configuredOptions = List.of(this.option(71L, 2), this.option(72L, 4));
        for (int type : List.of(1, 2)) {
            this.question.setQuestionType(type);
            ReflectionTestUtils.invokeMethod(this.service, "validateSubmission", List.of(this.snapshot),
                    List.of(this.question), configuredOptions, List.of());
            List<DimensionScoreResponse> scores = ReflectionTestUtils.invokeMethod(this.service, "calculateScores",
                    List.of(dimension), List.of(this.question), configuredOptions, List.of());
            assertEquals(0, scores.get(0).normalizedScore().compareTo(BigDecimal.ZERO));
            this.question.setRequiredFlag(1);
            assertThrows(BusinessException.class, () -> ReflectionTestUtils.invokeMethod(this.service,
                    "validateSubmission", List.of(this.snapshot), List.of(this.question), configuredOptions, List.of()));
            this.question.setRequiredFlag(0);
        }
    }

    private QuestionOptionEntity option(Long id, int score) {
        QuestionOptionEntity option = new QuestionOptionEntity();
        option.setId(id);
        option.setQuestionId(40L);
        option.setDimensionId(50L);
        option.setScoreValue(score);
        return option;
    }
}
