package com.personalink.server.ai;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.personalink.server.dto.AiGeneratedQuestion;
import com.personalink.server.dto.AiGeneratedSetup;
import com.personalink.server.dto.AiGeneratedResultRule;
import com.personalink.server.dto.AiGeneratedDimension;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import com.personalink.server.dto.AiGeneratedQuestionBatch;
import com.personalink.server.dto.AiGeneratedOption;
import com.personalink.server.dto.QuestionResponse;
import com.personalink.server.dto.QuestionSaveRequest;
import com.personalink.server.dto.ScoreDimensionResponse;
import com.personalink.server.dto.TestResponse;
import com.personalink.server.dto.TestVersionResponse;
import com.personalink.server.entity.AiGenerationTaskEntity;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.mapper.AiGenerationTaskMapper;
import com.personalink.server.service.ContentService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiQuestionGenerationWorkerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Test
    void generateValidSetupShouldRegenerateWithValidationFeedback() {
        DeepSeekClient deepSeekClient = mock(DeepSeekClient.class);
        AiGeneratedSetup invalid = this.setup(40, 50);
        AiGeneratedSetup valid = this.setup(50, 50);
        when(deepSeekClient.generateSetup(eq("deepseek-flash"), eq("勇气测试"), eq(1),
                eq("生成 100 道题"), anyString())).thenReturn(invalid, valid);
        AiQuestionGenerationWorker worker = new AiQuestionGenerationWorker(
                null, null, deepSeekClient, null);

        AiGeneratedSetup result = worker.generateValidSetup(
                2L, "deepseek-flash", "勇气测试", 1, "生成 100 道题");

        assertEquals(valid, result);
        ArgumentCaptor<String> feedbackCaptor = ArgumentCaptor.forClass(String.class);
        verify(deepSeekClient, times(2)).generateSetup(eq("deepseek-flash"), eq("勇气测试"), eq(1),
                eq("生成 100 道题"), feedbackCaptor.capture());
        assertEquals("", feedbackCaptor.getAllValues().get(0));
        assertTrue(feedbackCaptor.getAllValues().get(1).contains("断档或重叠"));
    }

    @Test
    void generateValidSetupShouldRetryInvalidJsonResponse() {
        DeepSeekClient deepSeekClient = mock(DeepSeekClient.class);
        AiGeneratedSetup valid = this.setup(50, 50);
        when(deepSeekClient.generateSetup(eq("deepseek-flash"), eq("勇气测试"), eq(1),
                eq("生成 100 道题"), anyString()))
                .thenThrow(new BusinessException(HttpStatus.BAD_GATEWAY, 50232,
                        "DeepSeek 返回的数据格式不合法"))
                .thenReturn(valid);
        AiQuestionGenerationWorker worker = new AiQuestionGenerationWorker(
                null, null, deepSeekClient, null);

        AiGeneratedSetup result = worker.generateValidSetup(
                2L, "deepseek-flash", "勇气测试", 1, "生成 100 道题");

        assertEquals(valid, result);
        verify(deepSeekClient, times(2)).generateSetup(eq("deepseek-flash"), eq("勇气测试"), eq(1),
                eq("生成 100 道题"), anyString());
    }

    @Test
    void generateValidSetupShouldRetryTemporaryDeepSeekFailure() {
        DeepSeekClient deepSeekClient = mock(DeepSeekClient.class);
        AiGeneratedSetup valid = this.setup(50, 50);
        when(deepSeekClient.generateSetup(eq("deepseek-flash"), eq("勇气测试"), eq(1),
                eq("生成 100 道题"), anyString()))
                .thenThrow(new BusinessException(HttpStatus.BAD_GATEWAY, 50231,
                        "DeepSeek 服务暂不可用，请稍后重试"))
                .thenReturn(valid);
        AiQuestionGenerationWorker worker = new AiQuestionGenerationWorker(
                null, null, deepSeekClient, null);

        AiGeneratedSetup result = worker.generateValidSetup(
                2L, "deepseek-flash", "勇气测试", 1, "生成 100 道题");

        assertEquals(valid, result);
        verify(deepSeekClient, times(2)).generateSetup(eq("deepseek-flash"), eq("勇气测试"), eq(1),
                eq("生成 100 道题"), anyString());
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


    @Test
    void invalidQuestionShouldBeSkippedWhileValidQuestionsAreKept() {
        DeepSeekClient client = mock(DeepSeekClient.class);
        AiGeneratedQuestionBatch generated = new AiGeneratedQuestionBatch(List.of(
                this.question(41, "新题41"), this.question(42, "重复题"), this.question(43, "新题43"),
                this.question(44, "新题43"), this.question(45, "新题45"), this.question(45, "另一题45")));
        when(client.generateQuestionBatch(anyString(), anyString(), eq(1), anyString(), anyList(),
                eq(41), eq(5), anyList(), anyString())).thenReturn(generated);
        List<AiGeneratedQuestionBatch> saved = new ArrayList<>();
        AiGeneratedQuestionBatch result = new AiQuestionGenerationWorker(null, null, client, null)
                .generateValidQuestionBatch(5L, "model", "主题", 1, "要求", List.of(), Set.of("bravery"),
                        41, 5, List.of("重复题"), new HashMap<>(), saved::add);
        assertEquals(List.of(41, 43), result.questions().stream().map(AiGeneratedQuestion::questionNo).toList());
        assertEquals(List.of(result), saved);
        verify(client).generateQuestionBatch(anyString(), anyString(), eq(1), anyString(), anyList(),
                eq(41), eq(5), anyList(), anyString());
    }

    @Test
    void malformedBatchShouldBeDeferredButInfrastructureAndPersistenceFailuresShouldStop() {
        DeepSeekClient client = mock(DeepSeekClient.class);
        AiQuestionGenerationWorker worker = new AiQuestionGenerationWorker(null, null, client, null);
        when(client.generateQuestionBatch(anyString(), anyString(), eq(1), anyString(), anyList(),
                eq(1), eq(1), anyList(), anyString()))
                .thenThrow(new BusinessException(HttpStatus.BAD_GATEWAY, 50232, "JSON无效"));
        assertTrue(worker.generateValidQuestionBatch(5L, "model", "主题", 1, "要求", List.of(),
                Set.of("bravery"), 1, 1, List.of(), new HashMap<>(), batch -> { }).questions().isEmpty());
        verify(client, times(3)).generateQuestionBatch(anyString(), anyString(), eq(1), anyString(), anyList(),
                eq(1), eq(1), anyList(), anyString());
        reset(client);
        BusinessException remoteFailure = new BusinessException(HttpStatus.BAD_GATEWAY, 50231, "服务故障");
        when(client.generateQuestionBatch(anyString(), anyString(), eq(1), anyString(), anyList(),
                eq(1), eq(1), anyList(), anyString())).thenThrow(remoteFailure);
        assertSame(remoteFailure, assertThrows(BusinessException.class, () -> worker.generateValidQuestionBatch(
                5L, "model", "主题", 1, "要求", List.of(), Set.of("bravery"), 1, 1, List.of(), new HashMap<>(), batch -> { })));
        verify(client, times(3)).generateQuestionBatch(anyString(), anyString(), eq(1), anyString(), anyList(),
                eq(1), eq(1), anyList(), anyString());
        reset(client);
        when(client.generateQuestionBatch(anyString(), anyString(), eq(1), anyString(), anyList(),
                eq(1), eq(1), anyList(), anyString())).thenReturn(new AiGeneratedQuestionBatch(List.of(this.question(1, "新题"))));
        RuntimeException failure = new DataAccessResourceFailureException("数据库不可用");
        assertSame(failure, assertThrows(RuntimeException.class, () -> worker.generateValidQuestionBatch(
                5L, "model", "主题", 1, "要求", List.of(), Set.of("bravery"), 1, 1, List.of(), new HashMap<>(),
                batch -> { throw failure; })));
        verify(client).generateQuestionBatch(anyString(), anyString(), eq(1), anyString(), anyList(),
                eq(1), eq(1), anyList(), anyString());
    }

    @Test
    void question43ShouldWaitUntilLaterBatchesAndOtherMissingQuestionsFinish() {
        assertEquals(List.of(41, 51, 43, 48, 43, 43, 43), this.runTask(false));
    }

    @Test
    void resumedTaskShouldFindMissingNumbersWithoutRegeneratingSavedQuestions() {
        assertEquals(List.of(43, 48, 43, 43, 43), this.runTask(true));
    }

    private List<Integer> runTask(boolean resume) {
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
        task.setGeneratedQuestionCount(resume ? 58 : 40);
        task.setTargetQuestionCount(60);
        task.setCompletedBatchCount(resume ? 6 : 4);
        task.setTotalBatchCount(6);
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
            if (update.getTaskStatus() != null) task.setTaskStatus(update.getTaskStatus());
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
        Map<Integer, String> saved = new HashMap<>();
        IntStream.rangeClosed(1, resume ? 60 : 40).filter(no -> no != 43 && no != 48)
                .forEach(no -> saved.put(no, "题" + no));
        when(content.listQuestions(7L)).thenAnswer(invocation -> saved.entrySet().stream()
                .map(entry -> new QuestionResponse(entry.getKey().longValue(), 7L, 1, 10L, 1, 1,
                        entry.getKey(), entry.getValue(), 1, entry.getKey(), List.of())).toList());
        List<Integer> requested = new ArrayList<>();
        when(client.generateQuestionBatch(anyString(), anyString(), eq(1), anyString(), anyList(),
                anyInt(), anyInt(), anyList(), anyString())).thenAnswer(invocation -> {
            int first = invocation.getArgument(5);
            int count = invocation.getArgument(6);
            List<String> excluded = invocation.getArgument(7);
            assertEquals(Set.copyOf(saved.values()), Set.copyOf(excluded), "每批都应传入所有已落库题干");
            requested.add(first);
            assertTrue(requested.size() < 10, "补题未收敛");
            boolean fail43 = requested.stream().filter(no -> no == 43).count() < 4;
            return new AiGeneratedQuestionBatch(IntStream.range(first, first + count)
                    .mapToObj(no -> this.question(no, no == 43 && fail43
                            ? "题1" : "题" + no)).toList());
        });
        when(content.appendGeneratedQuestions(eq(7L), anyList(), eq(1L))).thenAnswer(invocation -> {
            List<QuestionSaveRequest> requests = invocation.getArgument(1);
            // 模拟 SQL 的排序规则比 Java 更宽：初轮第 48 题被拒绝，其余题正常批量保存。
            List<QuestionSaveRequest> accepted = requests.stream()
                    .filter(request -> request.questionNo() != 48 || requested.contains(48)).toList();
            accepted.forEach(request -> assertNull(saved.put(request.questionNo(), request.questionText()),
                    "不得重新覆盖已保存题目"));
            return accepted.size();
        });
        new AiQuestionGenerationWorker(tasks, content, client, new TransactionTemplate(transactions)).generateAsync(5L);
        assertEquals(60, saved.size());
        assertEquals(60, task.getGeneratedQuestionCount());
        assertEquals(6, task.getCompletedBatchCount());
        assertEquals(3, task.getTaskStatus());
        return requested;
    }

    private AiGeneratedQuestion question(int no, String text) {
        return new AiGeneratedQuestion(1, "bravery", 1, 1, no, text, 1, no,
                List.of(new AiGeneratedOption("A", "马上尝试", null, 5, 1),
                        new AiGeneratedOption("B", "先观察", null, 1, 2)));
    }
}
