package com.personalink.server.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.dto.ReportResponse;
import com.personalink.server.entity.QuestionEntity;
import com.personalink.server.entity.ReportEntity;
import com.personalink.server.entity.ScoreDimensionEntity;
import com.personalink.server.enums.QuestionType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AssessmentServiceImplTest {

    @Test
    void reportResponseShouldHideLockedDeepResult() {
        AssessmentServiceImpl service = new AssessmentServiceImpl(
                null, null, null, null, null, null, null, null, null, null, null, new ObjectMapper());
        ReportEntity report = new ReportEntity();
        report.setId(1L);
        report.setAnswerSessionId(2L);
        report.setResultCode("DOG");
        report.setResultSnapshot("{\"resultName\":\"认真小狗型\",\"deepResult\":{\"text\":\"locked\"}}");

        ReportResponse response = ReflectionTestUtils.invokeMethod(service, "toReportResponse", report);

        assertFalse(response.resultSnapshot().has("deepResult"));
        assertEquals("认真小狗型", response.resultSnapshot().path("resultName").asText());
    }

    @Test
    void drawQuestionIdsShouldKeepConfiguredQuestionOrder() {
        AssessmentServiceImpl service = new AssessmentServiceImpl(
                null, null, null, null, null, null, null, null, null, null, null, new ObjectMapper());
        List<QuestionEntity> questions = List.of(
                this.question(30L, 11L, 1),
                this.question(10L, 12L, 2),
                this.question(20L, 13L, 3));
        List<Long> configuredOrder = questions.stream().map(QuestionEntity::getId).toList();
        List<ScoreDimensionEntity> dimensions = List.of(
                this.dimension(13L),
                this.dimension(12L),
                this.dimension(11L));

        for (int index = 0; index < 20; index++) {
            List<Long> questionIds = ReflectionTestUtils.invokeMethod(
                    service, "drawQuestionIds", 3, dimensions, questions, List.of());
            assertEquals(configuredOrder, questionIds);

            List<Long> selectedQuestionIds = ReflectionTestUtils.invokeMethod(
                    service, "drawQuestionIds", 2, List.of(), questions, List.of());
            assertEquals(configuredOrder.stream().filter(selectedQuestionIds::contains).toList(), selectedQuestionIds);
        }
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
