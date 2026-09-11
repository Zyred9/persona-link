package com.personalink.server.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.dto.AiGeneratedDimension;
import com.personalink.server.dto.AiGeneratedOption;
import com.personalink.server.dto.AiGeneratedQuestion;
import com.personalink.server.dto.AiGeneratedQuestionBatch;
import com.personalink.server.dto.AiGeneratedResultRule;
import com.personalink.server.dto.AiGeneratedSetup;
import com.personalink.server.dto.QuestionResponse;
import com.personalink.server.dto.ScoreDimensionResponse;
import com.personalink.server.dto.TestVersionResponse;
import com.personalink.server.dto.TestResponse;
import com.personalink.server.entity.AiGenerationTaskEntity;
import com.personalink.server.mapper.AiGenerationTaskMapper;
import com.personalink.server.service.ContentService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.exception.AiQuestionTextConflictException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
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
                eq(1), eq(1), eq(List.of()), anyString())).thenReturn(invalid, valid);
        AiQuestionGenerationWorker worker = new AiQuestionGenerationWorker(
                null, null, deepSeekClient, null);

        AiGeneratedQuestionBatch result = worker.generateValidQuestionBatch(
                2L, "deepseek-v4-flash", "勇气测试", 1, "生成 100 道题",
                List.of(new AiGeneratedDimension("bravery", "勇气", 0)), Set.of("bravery"), 1, 1,
                List.of(), batch -> { });

        assertEquals(valid, result);
        ArgumentCaptor<String> feedbackCaptor = ArgumentCaptor.forClass(String.class);
        verify(deepSeekClient, times(2)).generateQuestionBatch(eq("deepseek-v4-flash"), eq("勇气测试"), eq(1),
                eq("生成 100 道题"), eq(List.of(new AiGeneratedDimension("bravery", "勇气", 0))),
                eq(1), eq(1), eq(List.of()), feedbackCaptor.capture());
        assertTrue(feedbackCaptor.getAllValues().get(1).contains("单选题维度或选择数量无效"));
    }

    private AiGeneratedSetup setup(int firstMax, int secondMin) {
        return new AiGeneratedSetup(
                List.of(new AiGeneratedDimension("bravery", "勇气", 0)),
                List.of(this.rule("LOW", 0, firstMax), this.rule("HIGH", secondMin, 100)));
    }

    @Test
    void repeatedQuestionTextShouldRegenerateBeforePersistingAndPassExistingTexts() {
        DeepSeekClient client = mock(DeepSeekClient.class);
        List<String> existing = List.of(" 前一批的问题 ");
        AiGeneratedQuestionBatch duplicate = this.batch(21, "前一批的问题");
        AiGeneratedQuestionBatch valid = this.batch(21, "全新的问题");
        when(client.generateQuestionBatch(anyString(), anyString(), eq(1), anyString(), anyList(),
                eq(21), eq(1), eq(existing), anyString())).thenReturn(duplicate, valid);
        List<AiGeneratedQuestionBatch> saved = new ArrayList<>();
        AiQuestionGenerationWorker worker = new AiQuestionGenerationWorker(null, null, client, null);

        assertSame(valid, worker.generateValidQuestionBatch(5L, "model", "主题", 1, "要求",
                List.of(), Set.of("bravery"), 21, 1, existing, saved::add));

        assertEquals(List.of(valid), saved);
        ArgumentCaptor<String> feedback = ArgumentCaptor.forClass(String.class);
        verify(client, times(2)).generateQuestionBatch(anyString(), anyString(), eq(1), anyString(), anyList(),
                eq(21), eq(1), eq(existing), feedback.capture());
        assertTrue(feedback.getAllValues().get(1).contains("前一批的问题"));
    }

    @Test
    void duplicateInsideBatchShouldRegenerateWholeBatch() {
        DeepSeekClient client = mock(DeepSeekClient.class);
        AiGeneratedQuestionBatch duplicate = this.batch(1, "同一个题干", " 同一个题干 ");
        AiGeneratedQuestionBatch valid = this.batch(1, "第一个题干", "第二个题干");
        when(client.generateQuestionBatch(anyString(), anyString(), eq(1), anyString(), anyList(),
                eq(1), eq(2), eq(List.of()), anyString())).thenReturn(duplicate, valid);
        List<AiGeneratedQuestionBatch> saved = new ArrayList<>();
        AiQuestionGenerationWorker worker = new AiQuestionGenerationWorker(null, null, client, null);

        assertSame(valid, worker.generateValidQuestionBatch(5L, "model", "主题", 1, "要求",
                List.of(), Set.of("bravery"), 1, 2, List.of(), saved::add));
        assertEquals(List.of(valid), saved);
    }

    @Test
    void repeatedQuestionTextShouldStopAfterThreeAttemptsWithoutPersisting() {
        DeepSeekClient client = mock(DeepSeekClient.class);
        List<String> existing = List.of("重复题干");
        when(client.generateQuestionBatch(anyString(), anyString(), eq(1), anyString(), anyList(),
                eq(21), eq(1), eq(existing), anyString())).thenReturn(this.batch(21, "重复题干"));
        List<AiGeneratedQuestionBatch> saved = new ArrayList<>();
        AiQuestionGenerationWorker worker = new AiQuestionGenerationWorker(null, null, client, null);

        assertThrows(IllegalArgumentException.class, () -> worker.generateValidQuestionBatch(
                5L, "model", "主题", 1, "要求", List.of(), Set.of("bravery"), 21, 1, existing, saved::add));
        assertTrue(saved.isEmpty());
        verify(client, times(3)).generateQuestionBatch(anyString(), anyString(), eq(1), anyString(), anyList(),
                eq(21), eq(1), eq(existing), anyString());
    }

    @Test
    void persistenceTextConflictShouldRegenerateAndOtherPersistenceErrorsShouldFailFast() {
        DeepSeekClient client = mock(DeepSeekClient.class);
        AiGeneratedQuestionBatch first = this.batch(21, "Café");
        AiGeneratedQuestionBatch valid = this.batch(21, "新的情境");
        when(client.generateQuestionBatch(anyString(), anyString(), eq(1), anyString(), anyList(),
                eq(21), eq(1), eq(List.of("Cafe")), anyString())).thenReturn(first, valid);
        List<AiGeneratedQuestionBatch> attempted = new ArrayList<>();
        AiQuestionGenerationWorker worker = new AiQuestionGenerationWorker(null, null, client, null);
        worker.generateValidQuestionBatch(5L, "model", "主题", 1, "要求", List.of(), Set.of("bravery"),
                21, 1, List.of("Cafe"), batch -> {
                    attempted.add(batch);
                    if (batch == first) {
                        throw new AiQuestionTextConflictException("数据库判定题干已存在：Cafe");
                    }
                });
        assertEquals(List.of(first, valid), attempted);

        for (RuntimeException failure : List.of(new BusinessException(400, "AI 生成题号已存在"),
                new DataAccessResourceFailureException("数据库不可用"),
                new IllegalArgumentException("持久化参数错误"))) {
            DeepSeekClient failingClient = mock(DeepSeekClient.class);
            when(failingClient.generateQuestionBatch(anyString(), anyString(), eq(1), anyString(), anyList(),
                    eq(21), eq(1), eq(List.of()), anyString())).thenReturn(valid);
            AiQuestionGenerationWorker failingWorker = new AiQuestionGenerationWorker(null, null, failingClient, null);
            assertSame(failure, assertThrows(RuntimeException.class, () -> failingWorker.generateValidQuestionBatch(
                    5L, "model", "主题", 1, "要求", List.of(), Set.of("bravery"), 21, 1, List.of(),
                    batch -> { throw failure; })));
            verify(failingClient, times(1)).generateQuestionBatch(anyString(), anyString(), eq(1), anyString(), anyList(),
                    eq(21), eq(1), eq(List.of()), anyString());
        }
    }

    private AiGeneratedQuestionBatch batch(int firstNo, String... texts) {
        List<AiGeneratedQuestion> questions = new ArrayList<>();
        for (int index = 0; index < texts.length; index++) {
            questions.add(new AiGeneratedQuestion(1, "bravery", 1, 1, firstNo + index, texts[index], 1,
                    firstNo + index, this.questionBatch("bravery").questions().get(0).options()));
        }
        return new AiGeneratedQuestionBatch(questions);
    }

    @Test
    void resumedTaskShouldRollbackConflictingAttemptAndAdvanceProgressOnlyAfterSuccessfulAppend() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
                AiGenerationTaskEntity.class);
        AiGenerationTaskMapper tasks = mock(AiGenerationTaskMapper.class);
        ContentService content = mock(ContentService.class);
        DeepSeekClient client = mock(DeepSeekClient.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        AiGenerationTaskEntity task = new AiGenerationTaskEntity();
        task.setId(5L);
        task.setVersionId(7L);
        task.setTaskStatus(2);
        task.setOperatorId(1L);
        task.setDimensionGeneratedFlag(1);
        task.setResultRuleGeneratedFlag(1);
        task.setGeneratedQuestionCount(20);
        task.setTargetQuestionCount(30);
        task.setCompletedBatchCount(2);
        task.setTotalBatchCount(3);
        task.setModelName("model");
        task.setPromptText("要求");
        when(tasks.update(isNull(), any())).thenReturn(1);
        when(tasks.selectById(5L)).thenReturn(task);
        when(tasks.selectOne(any(), eq(false))).thenReturn(task);
        when(tasks.updateById(any(AiGenerationTaskEntity.class))).thenAnswer(invocation -> {
            AiGenerationTaskEntity update = invocation.getArgument(0);
            if (update.getGeneratedQuestionCount() != null) {
                task.setGeneratedQuestionCount(update.getGeneratedQuestionCount());
                task.setCompletedBatchCount(update.getCompletedBatchCount());
            }
            return 1;
        });
        TestVersionResponse version = mock(TestVersionResponse.class);
        when(version.testId()).thenReturn(9L);
        when(version.dimensions()).thenReturn(List.of(new ScoreDimensionResponse(10L, 7L, "bravery", "勇气", 0)));
        TestResponse test = mock(TestResponse.class);
        when(test.getTestName()).thenReturn("主题");
        when(test.getTestType()).thenReturn(1);
        when(content.getVersion(7L)).thenReturn(version);
        when(content.getTest(9L)).thenReturn(test);
        List<String> existing = IntStream.rangeClosed(1, 20).mapToObj(number -> "旧题" + number).toList();
        when(content.listQuestions(7L)).thenReturn(IntStream.rangeClosed(1, 20)
                .mapToObj(number -> new QuestionResponse((long) number, 7L, 1, 10L, 1, 1,
                        number, "旧题" + number, 1, number, List.of())).toList());
        AiGeneratedQuestionBatch generated = this.batch(21,
                IntStream.rangeClosed(21, 30).mapToObj(number -> "新题" + number).toArray(String[]::new));
        when(client.generateQuestionBatch(eq("model"), eq("主题"), eq(1), eq("要求"), anyList(),
                eq(21), eq(10), eq(existing), anyString())).thenReturn(generated);
        List<Integer> countsAtAppend = new ArrayList<>();
        doAnswer(invocation -> {
            countsAtAppend.add(task.getGeneratedQuestionCount());
            assertEquals(2, task.getCompletedBatchCount());
            if (countsAtAppend.size() == 1) {
                throw new AiQuestionTextConflictException("SQL 判重：旧题1");
            }
            verify(transactions).rollback(any());
            return null;
        }).when(content).appendGeneratedQuestions(eq(7L), anyList(), eq(1L));

        new AiQuestionGenerationWorker(tasks, content, client, new TransactionTemplate(transactions))
                .generateAsync(5L);

        assertEquals(List.of(20, 20), countsAtAppend);
        assertEquals(30, task.getGeneratedQuestionCount());
        assertEquals(3, task.getCompletedBatchCount());
        verify(transactions, times(1)).rollback(any());
        verify(client, times(2)).generateQuestionBatch(eq("model"), eq("主题"), eq(1), eq("要求"), anyList(),
                eq(21), eq(10), eq(existing), anyString());
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
