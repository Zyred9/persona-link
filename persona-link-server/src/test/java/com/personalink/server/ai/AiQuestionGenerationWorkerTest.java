package com.personalink.server.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.dto.AiGeneratedDimension;
import com.personalink.server.dto.AiGeneratedOption;
import com.personalink.server.dto.AiGeneratedQuestion;
import com.personalink.server.dto.AiGeneratedQuestionBatch;
import com.personalink.server.dto.AiGeneratedResultRule;
import com.personalink.server.dto.AiGeneratedSetup;
import com.personalink.server.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiQuestionGenerationWorkerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void generateValidSetupShouldRegenerateWithValidationFeedback() {
        DeepSeekClient deepSeekClient = mock(DeepSeekClient.class);
        AiGeneratedSetup invalid = this.setup(40, 50);
        AiGeneratedSetup valid = this.setup(50, 50);
        when(deepSeekClient.generateSetup(eq("deepseek-v4-flash"), eq("勇气测试"), eq(1),
                eq("生成 100 道题"), anyString())).thenReturn(invalid, valid);
        AiQuestionGenerationWorker worker = new AiQuestionGenerationWorker(
                null, null, deepSeekClient, null);

        AiGeneratedSetup result = worker.generateValidSetup(
                2L, "deepseek-v4-flash", "勇气测试", 1, "生成 100 道题");

        assertEquals(valid, result);
        ArgumentCaptor<String> feedbackCaptor = ArgumentCaptor.forClass(String.class);
        verify(deepSeekClient, times(2)).generateSetup(eq("deepseek-v4-flash"), eq("勇气测试"), eq(1),
                eq("生成 100 道题"), feedbackCaptor.capture());
        assertEquals("", feedbackCaptor.getAllValues().get(0));
        assertTrue(feedbackCaptor.getAllValues().get(1).contains("断档或重叠"));
    }

    @Test
    void generateValidSetupShouldRetryInvalidJsonResponse() {
        DeepSeekClient deepSeekClient = mock(DeepSeekClient.class);
        AiGeneratedSetup valid = this.setup(50, 50);
        when(deepSeekClient.generateSetup(eq("deepseek-v4-flash"), eq("勇气测试"), eq(1),
                eq("生成 100 道题"), anyString()))
                .thenThrow(new BusinessException(HttpStatus.BAD_GATEWAY, 50232,
                        "DeepSeek 返回的数据格式不合法"))
                .thenReturn(valid);
        AiQuestionGenerationWorker worker = new AiQuestionGenerationWorker(
                null, null, deepSeekClient, null);

        AiGeneratedSetup result = worker.generateValidSetup(
                2L, "deepseek-v4-flash", "勇气测试", 1, "生成 100 道题");

        assertEquals(valid, result);
        verify(deepSeekClient, times(2)).generateSetup(eq("deepseek-v4-flash"), eq("勇气测试"), eq(1),
                eq("生成 100 道题"), anyString());
    }

    @Test
    void generateValidSetupShouldRetryTemporaryDeepSeekFailure() {
        DeepSeekClient deepSeekClient = mock(DeepSeekClient.class);
        AiGeneratedSetup valid = this.setup(50, 50);
        when(deepSeekClient.generateSetup(eq("deepseek-v4-flash"), eq("勇气测试"), eq(1),
                eq("生成 100 道题"), anyString()))
                .thenThrow(new BusinessException(HttpStatus.BAD_GATEWAY, 50231,
                        "DeepSeek 服务暂不可用，请稍后重试"))
                .thenReturn(valid);
        AiQuestionGenerationWorker worker = new AiQuestionGenerationWorker(
                null, null, deepSeekClient, null);

        AiGeneratedSetup result = worker.generateValidSetup(
                2L, "deepseek-v4-flash", "勇气测试", 1, "生成 100 道题");

        assertEquals(valid, result);
        verify(deepSeekClient, times(2)).generateSetup(eq("deepseek-v4-flash"), eq("勇气测试"), eq(1),
                eq("生成 100 道题"), anyString());
    }

    @Test
    void generateValidQuestionBatchShouldRegenerateWithValidationFeedback() {
        DeepSeekClient deepSeekClient = mock(DeepSeekClient.class);
        AiGeneratedQuestionBatch invalid = this.questionBatch("unknown");
        AiGeneratedQuestionBatch valid = this.questionBatch("bravery");
        when(deepSeekClient.generateQuestionBatch(eq("deepseek-v4-flash"), eq("勇气测试"), eq(1),
                eq("生成 100 道题"), eq(List.of(new AiGeneratedDimension("bravery", "勇气", 0))),
                eq(1), eq(1), anyString())).thenReturn(invalid, valid);
        AiQuestionGenerationWorker worker = new AiQuestionGenerationWorker(
                null, null, deepSeekClient, null);

        AiGeneratedQuestionBatch result = worker.generateValidQuestionBatch(
                2L, "deepseek-v4-flash", "勇气测试", 1, "生成 100 道题",
                List.of(new AiGeneratedDimension("bravery", "勇气", 0)), Set.of("bravery"), 1, 1);

        assertEquals(valid, result);
        ArgumentCaptor<String> feedbackCaptor = ArgumentCaptor.forClass(String.class);
        verify(deepSeekClient, times(2)).generateQuestionBatch(eq("deepseek-v4-flash"), eq("勇气测试"), eq(1),
                eq("生成 100 道题"), eq(List.of(new AiGeneratedDimension("bravery", "勇气", 0))),
                eq(1), eq(1), feedbackCaptor.capture());
        assertTrue(feedbackCaptor.getAllValues().get(1).contains("单选题维度或选择数量无效"));
    }

    private AiGeneratedSetup setup(int firstMax, int secondMin) {
        return new AiGeneratedSetup(
                List.of(new AiGeneratedDimension("bravery", "勇气", 0)),
                List.of(this.rule("LOW", 0, firstMax), this.rule("HIGH", secondMin, 100)));
    }

    private AiGeneratedResultRule rule(String resultCode, int scoreMin, int scoreMax) {
        return new AiGeneratedResultRule("bravery", resultCode, resultCode,
                BigDecimal.valueOf(scoreMin), BigDecimal.valueOf(scoreMax),
                this.objectMapper.createObjectNode().put("text", resultCode), null, null, scoreMin);
    }

    private AiGeneratedQuestionBatch questionBatch(String dimensionCode) {
        AiGeneratedQuestion question = new AiGeneratedQuestion(
                1, dimensionCode, 1, 1, 1, "面对陌生挑战时你会？", 1, 1,
                List.of(new AiGeneratedOption("A", "马上尝试", null, 5, 1),
                        new AiGeneratedOption("B", "先观察", null, 1, 2)));
        return new AiGeneratedQuestionBatch(List.of(question));
    }
}
