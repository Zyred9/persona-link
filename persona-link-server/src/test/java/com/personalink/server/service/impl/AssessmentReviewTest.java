package com.personalink.server.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.entity.*;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.mapper.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssessmentReviewTest {
    private final AnswerSessionMapper sessions = mock(AnswerSessionMapper.class);
    private final TestVersionMapper versions = mock(TestVersionMapper.class);
    private final QuestionMapper questions = mock(QuestionMapper.class);
    private final QuestionOptionMapper options = mock(QuestionOptionMapper.class);
    private final AnswerSessionQuestionMapper snapshots = mock(AnswerSessionQuestionMapper.class);
    private final AnswerDetailMapper details = mock(AnswerDetailMapper.class);
    private final PairSessionMapper pairs = mock(PairSessionMapper.class);
    private final AnswerSessionEntity own = this.session(1L, "owner");
    private final AnswerSessionEntity other = this.session(2L, "friend");
    private final PairSessionEntity pair = new PairSessionEntity();
    private AssessmentServiceImpl service;

    @BeforeEach
    void setup() {
        for (Class<?> type : List.of(AnswerSessionEntity.class, AnswerSessionQuestionEntity.class,
                QuestionEntity.class, QuestionOptionEntity.class, AnswerDetailEntity.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "review"), type);
        }
        this.service = new AssessmentServiceImpl(null, this.versions, null, this.questions, this.options,
                null, this.snapshots, this.details, null, this.pairs, null, new ObjectMapper(), null);
        ReflectionTestUtils.setField(this.service, "baseMapper", this.sessions);
        when(this.sessions.selectById(1L)).thenReturn(this.own);
        when(this.sessions.selectById(2L)).thenReturn(this.other);
        TestVersionEntity version = new TestVersionEntity();
        version.setId(10L); version.setTestId(20L); version.setTitle("历史版本"); version.setVersionStatus(5);
        when(this.versions.selectById(10L)).thenReturn(version);
        QuestionEntity question = new QuestionEntity();
        question.setId(30L); question.setVersionId(10L); question.setQuestionText("历史题目");
        question.setQuestionType(1); question.setRequiredFlag(1);
        question.setMinSelectCount(1); question.setMaxSelectCount(1);
        QuestionOptionEntity option = new QuestionOptionEntity();
        option.setId(40L); option.setQuestionId(30L); option.setOptionText("原选项");
        AnswerSessionQuestionEntity snapshot = new AnswerSessionQuestionEntity(); snapshot.setQuestionId(30L);
        AnswerDetailEntity detail = new AnswerDetailEntity(); detail.setQuestionId(30L); detail.setOptionId(40L);
        when(this.questions.selectList(any())).thenReturn(List.of(question));
        when(this.options.selectList(any())).thenReturn(List.of(option));
        when(this.snapshots.selectList(any())).thenReturn(List.of(snapshot));
        when(this.details.selectList(any())).thenReturn(List.of(detail));
        this.pair.setInitiatorOpenId("owner"); this.pair.setPartnerOpenId("friend");
        this.pair.setInitiatorAnswerSessionId(1L); this.pair.setPartnerAnswerSessionId(2L);
        this.pair.setInitiatorVisibleFlag(1); this.pair.setPartnerVisibleFlag(1);
        when(this.pairs.selectById(50L)).thenReturn(this.pair);
    }

    @Test
    void ownSubmittedHistoryUsesOriginalVersionAndSelection() {
        var result = this.service.review("owner", 1L).participants().get(0);
        assertEquals("历史版本", result.title());
        assertEquals(List.of("40"), result.questions().get(0).selectedOptionIds());
        assertThrows(BusinessException.class, () -> this.service.review("stranger", 1L));
        this.own.setAnswerStatus(1);
        assertThrows(BusinessException.class, () -> this.service.review("owner", 1L));
    }

    @Test
    void pairOnlyDisclosesSubmittedParticipantsAndRespectsPrivacy() {
        this.other.setAnswerStatus(1);
        assertEquals(1, this.service.reviewPair("owner", 50L).participants().size());
        assertThrows(BusinessException.class, () -> this.service.reviewPair("friend", 50L));
        this.other.setAnswerStatus(3);
        var result = this.service.reviewPair("friend", 50L).participants();
        assertEquals("2", result.get(0).answerSessionId());
        assertEquals("partner", result.get(1).key());
        assertThrows(BusinessException.class, () -> this.service.reviewPair("stranger", 50L));
        this.pair.setPartnerVisibleFlag(0);
        assertEquals(1, this.service.reviewPair("owner", 50L).participants().size());
        assertThrows(BusinessException.class, () -> this.service.reviewPair("friend", 50L));
        this.pair.setPartnerVisibleFlag(1);
        when(this.sessions.selectById(2L)).thenReturn(null);
        assertEquals(1, this.service.reviewPair("owner", 50L).participants().size());
    }

    @Test
    void missingQuestionSnapshotOrSelectedOptionFailsExplicitly() {
        QuestionOptionEntity unrelated = new QuestionOptionEntity();
        unrelated.setId(41L); unrelated.setQuestionId(30L);
        when(this.options.selectList(any())).thenReturn(List.of(unrelated));
        assertThrows(BusinessException.class, () -> this.service.review("owner", 1L));
        when(this.options.selectList(any())).thenReturn(List.of());
        assertThrows(BusinessException.class, () -> this.service.review("owner", 1L));
        when(this.snapshots.selectList(any())).thenReturn(List.of());
        assertThrows(BusinessException.class, () -> this.service.review("owner", 1L));
    }

    @Test
    void reviewPreservesSnapshotOrderRatherThanQuestionQueryOrder() {
        QuestionEntity first = this.questions.selectList(null).get(0);
        QuestionEntity second = new QuestionEntity();
        second.setId(31L); second.setVersionId(10L); second.setQuestionText("第二道原题");
        second.setQuestionType(1); second.setRequiredFlag(0);
        second.setMinSelectCount(1); second.setMaxSelectCount(1);
        QuestionOptionEntity option = new QuestionOptionEntity();
        option.setId(41L); option.setQuestionId(31L);
        QuestionOptionEntity firstOption = this.options.selectList(null).get(0);
        AnswerSessionQuestionEntity firstSnapshot = new AnswerSessionQuestionEntity();
        firstSnapshot.setQuestionId(31L); firstSnapshot.setSortNo(1);
        AnswerSessionQuestionEntity secondSnapshot = new AnswerSessionQuestionEntity();
        secondSnapshot.setQuestionId(30L); secondSnapshot.setSortNo(2);
        when(this.questions.selectList(any())).thenReturn(List.of(first, second));
        when(this.options.selectList(any())).thenReturn(List.of(firstOption, option));
        when(this.snapshots.selectList(any())).thenReturn(List.of(firstSnapshot, secondSnapshot));
        var response = this.service.review("owner", 1L).participants().get(0);
        assertEquals(List.of("31", "30"), response.questions().stream().map(q -> q.questionId()).toList());
        verify(this.snapshots).selectList(argThat(query -> query.getSqlSegment().contains("sort_no ASC")));
    }

    private AnswerSessionEntity session(Long id, String openId) {
        AnswerSessionEntity session = new AnswerSessionEntity();
        session.setId(id); session.setOpenId(openId); session.setVersionId(10L);
        session.setAnswerType(1); session.setAnswerStatus(3);
        return session;
    }
}
