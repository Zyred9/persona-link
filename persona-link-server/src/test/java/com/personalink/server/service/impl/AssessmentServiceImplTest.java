package com.personalink.server.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.dto.ReportResponse;
import com.personalink.server.entity.QuestionEntity;
import com.personalink.server.entity.ReportEntity;
import com.personalink.server.entity.ScoreDimensionEntity;
import com.personalink.server.enums.QuestionType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssessmentServiceImplTest {

    @Test
    void directReportsCannotBypassAccessService() {
        var access = org.mockito.Mockito.mock(com.personalink.server.service.ReportAccessService.class);
        var reports = org.mockito.Mockito.mock(com.personalink.server.mapper.ReportMapper.class);
        var answers = org.mockito.Mockito.mock(com.personalink.server.mapper.AnswerSessionMapper.class);
        ReportEntity report = new ReportEntity();
        report.setId(1L);
        report.setAnswerSessionId(2L);
        var answer = new com.personalink.server.entity.AnswerSessionEntity();
        answer.setOpenId("owner");
        org.mockito.Mockito.when(reports.selectById(1L)).thenReturn(report);
        org.mockito.Mockito.when(answers.selectById(2L)).thenReturn(answer);
        var denied = new com.personalink.server.exception.BusinessException(
                org.springframework.http.HttpStatus.FORBIDDEN, 40301, "locked");
        org.mockito.Mockito.doThrow(denied).when(access).requireRead("owner", 1, 1L);
        AssessmentServiceImpl service = new AssessmentServiceImpl(
                null, null, null, null, null, null, null, null, reports, null, null, new ObjectMapper(), access);
        ReflectionTestUtils.setField(service, "baseMapper", answers);
        org.junit.jupiter.api.Assertions.assertThrows(com.personalink.server.exception.BusinessException.class,
                () -> service.getReport("owner", 1L, null));
        org.mockito.Mockito.doThrow(denied).when(access).requireRead("owner", 2, 3L);
        var pairs = new PairAssessmentServiceImpl(null, null, null, null, null, null, new ObjectMapper(), access);
        org.junit.jupiter.api.Assertions.assertThrows(com.personalink.server.exception.BusinessException.class,
                () -> pairs.getReport("owner", 3L));
    }

    @Test
    void historyAndBothSubmitBranchesKeepTheirGates() throws Exception {
        String service = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/personalink/server/service/impl/AssessmentServiceImpl.java"));
        org.junit.jupiter.api.Assertions.assertTrue(service.contains("toSubmissionResponse(openId, existingReport)"));
        org.junit.jupiter.api.Assertions.assertTrue(service.contains("toSubmissionResponse(openId, savedReport)"));
        String mapper = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/resources/mapper/ReportMapper.xml"));
        org.junit.jupiter.api.Assertions.assertTrue(mapper.contains("CASE WHEN access.id IS NOT NULL THEN r.result_code"));
        org.junit.jupiter.api.Assertions.assertTrue(mapper.contains("CASE WHEN access.id IS NOT NULL"));
        org.junit.jupiter.api.Assertions.assertTrue(mapper.contains("access.open_id = s.open_id"));
    }

    @Test
    void submissionNeverReturnsLockedResultContent() {
        var access = org.mockito.Mockito.mock(com.personalink.server.service.ReportAccessService.class);
        AssessmentServiceImpl service = new AssessmentServiceImpl(
                null, null, null, null, null, null, null, null, null, null, null, new ObjectMapper(), access);
        ReportEntity report = new ReportEntity();
        report.setId(1L);
        report.setAnswerSessionId(2L);
        report.setResultCode("SECRET");
        report.setResultSnapshot("{\"resultName\":\"secret\"}");
        ReportResponse response = ReflectionTestUtils.invokeMethod(service, "toSubmissionResponse", "owner", report);
        org.junit.jupiter.api.Assertions.assertNull(response.resultCode());
        org.junit.jupiter.api.Assertions.assertNull(response.resultSnapshot());
        assertEquals("1", response.reportId());
    }

    @Test
    void reportResponseShouldHideLockedDeepResult() {
        AssessmentServiceImpl service = new AssessmentServiceImpl(
                null, null, null, null, null, null, null, null, null, null, null, new ObjectMapper(), null);
        ReportEntity report = new ReportEntity();
        report.setId(1L);
        report.setAnswerSessionId(2L);
        report.setResultCode("DOG");
        report.setResultSnapshot("{\"resultName\":\"认真小狗型\",\"deepResult\":{\"text\":\"locked\"}}");
        report.setReportNo("report-no-123");

        ReportResponse response = ReflectionTestUtils.invokeMethod(service, "toReportResponse", report);

        assertFalse(response.resultSnapshot().has("deepResult"));
        assertEquals("认真小狗型", response.resultSnapshot().path("resultName").asText());
        assertEquals("report-no-123", response.shareToken());
    }

    @Test
    void shareTokenAllowsAnyViewerButKeepsOwnerAdGate() {
        var access = org.mockito.Mockito.mock(com.personalink.server.service.ReportAccessService.class);
        var reports = org.mockito.Mockito.mock(com.personalink.server.mapper.ReportMapper.class);
        var answers = org.mockito.Mockito.mock(com.personalink.server.mapper.AnswerSessionMapper.class);
        ReportEntity report = new ReportEntity();
        report.setId(1L);
        report.setAnswerSessionId(2L);
        report.setResultCode("DOG");
        report.setResultSnapshot("{\"resultName\":\"认真小狗型\"}");
        report.setReportNo("report-no-123");
        var answer = new com.personalink.server.entity.AnswerSessionEntity();
        answer.setOpenId("owner");
        org.mockito.Mockito.when(reports.selectById(1L)).thenReturn(report);
        org.mockito.Mockito.when(answers.selectById(2L)).thenReturn(answer);
        AssessmentServiceImpl service = new AssessmentServiceImpl(
                null, null, null, null, null, null, null, null, reports, null, null, new ObjectMapper(), access);
        ReflectionTestUtils.setField(service, "baseMapper", answers);

        ReportResponse shared = service.getReport("guest", 1L, "report-no-123");
        assertEquals("认真小狗型", shared.resultSnapshot().path("resultName").asText());
        assertEquals("report-no-123", shared.shareToken());
        org.mockito.Mockito.verify(access, org.mockito.Mockito.never())
                .requireRead(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyLong());

        org.junit.jupiter.api.Assertions.assertThrows(com.personalink.server.exception.BusinessException.class,
                () -> service.getReport("guest", 1L, "invalid-token"));

        var denied = new com.personalink.server.exception.BusinessException(
                org.springframework.http.HttpStatus.FORBIDDEN, 40301, "locked");
        org.mockito.Mockito.doThrow(denied).when(access).requireRead("owner", 1, 1L);
        org.junit.jupiter.api.Assertions.assertThrows(com.personalink.server.exception.BusinessException.class,
                () -> service.getReport("owner", 1L, "report-no-123"));
    }

    @Test
    void ownerReadReturnsReportNumberAsShareToken() {
        var access = org.mockito.Mockito.mock(com.personalink.server.service.ReportAccessService.class);
        var reports = org.mockito.Mockito.mock(com.personalink.server.mapper.ReportMapper.class);
        var answers = org.mockito.Mockito.mock(com.personalink.server.mapper.AnswerSessionMapper.class);
        ReportEntity report = new ReportEntity();
        report.setId(1L);
        report.setAnswerSessionId(2L);
        report.setResultCode("DOG");
        report.setResultSnapshot("{\"resultName\":\"认真小狗型\"}");
        report.setReportNo("report-no-123");
        var answer = new com.personalink.server.entity.AnswerSessionEntity();
        answer.setOpenId("owner");
        org.mockito.Mockito.when(reports.selectById(1L)).thenReturn(report);
        org.mockito.Mockito.when(answers.selectById(2L)).thenReturn(answer);
        AssessmentServiceImpl service = new AssessmentServiceImpl(
                null, null, null, null, null, null, null, null, reports, null, null, new ObjectMapper(), access);
        ReflectionTestUtils.setField(service, "baseMapper", answers);

        ReportResponse response = service.getReport("owner", 1L, null);

        assertEquals("report-no-123", response.shareToken(), "本人读取复用报告编号作为分享令牌");
    }

    @Test
    void drawQuestionIdsShouldDrawRandomQuestionsInRandomOrder() {
        AssessmentServiceImpl service = new AssessmentServiceImpl(
                null, null, null, null, null, null, null, null, null, null, null, new ObjectMapper(), null);
        List<QuestionEntity> questions = List.of(
                this.question(30L, 11L, 1),
                this.question(10L, 12L, 2),
                this.question(20L, 13L, 3));
        List<ScoreDimensionEntity> dimensions = List.of(
                this.dimension(13L),
                this.dimension(12L),
                this.dimension(11L));
        Set<Long> allQuestionIds = Set.of(30L, 10L, 20L);
        Set<List<Long>> orders = new HashSet<>();

        for (int index = 0; index < 50; index++) {
            List<Long> questionIds = ReflectionTestUtils.invokeMethod(
                    service, "drawQuestionIds", 3, dimensions, questions, List.of());
            assertEquals(allQuestionIds, new HashSet<>(questionIds));
            orders.add(questionIds);

            List<Long> selectedQuestionIds = ReflectionTestUtils.invokeMethod(
                    service, "drawQuestionIds", 2, List.of(), questions, List.of());
            assertEquals(2, new HashSet<>(selectedQuestionIds).size());
            assertTrue(allQuestionIds.containsAll(selectedQuestionIds));
        }
        assertTrue(orders.size() > 1, "抽题顺序应随机，不能固定为配置顺序");
    }

    @Test
    void drawQuestionIdsShouldPlaceMultipleChoiceLast() {
        AssessmentServiceImpl service = new AssessmentServiceImpl(
                null, null, null, null, null, null, null, null, null, null, null, new ObjectMapper(), null);
        QuestionEntity multipleFirst = this.question(30L, 11L, 1);
        multipleFirst.setQuestionType(QuestionType.MULTIPLE.getCode());
        QuestionEntity multipleLast = this.question(40L, 14L, 4);
        multipleLast.setQuestionType(QuestionType.MULTIPLE.getCode());
        List<QuestionEntity> questions = List.of(
                multipleFirst,
                this.question(10L, 12L, 2),
                this.question(20L, 13L, 3),
                multipleLast);
        Set<List<Long>> orders = new HashSet<>();

        for (int index = 0; index < 50; index++) {
            List<Long> questionIds = ReflectionTestUtils.invokeMethod(
                    service, "drawQuestionIds", 4, List.of(), questions, List.of());
            assertEquals(Set.of(10L, 20L, 30L, 40L), new HashSet<>(questionIds));
            assertTrue(Set.of(10L, 20L).containsAll(questionIds.subList(0, 2)));
            assertTrue(Set.of(30L, 40L).containsAll(questionIds.subList(2, 4)));
            orders.add(questionIds);
        }
        assertTrue(orders.size() > 1, "同一题型内的抽题顺序应随机");
    }

    private QuestionEntity question(Long id, Long dimensionId, int questionNo) {
        QuestionEntity question = new QuestionEntity();
        question.setId(id);
        question.setDimensionId(dimensionId);
        question.setQuestionType(QuestionType.SINGLE.getCode());
        question.setQuestionNo(questionNo);
        return question;
    }

    private ScoreDimensionEntity dimension(Long id) {
        ScoreDimensionEntity dimension = new ScoreDimensionEntity();
        dimension.setId(id);
        return dimension;
    }
}
