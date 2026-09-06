package com.personalink.server.ai;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.personalink.server.config.ImageGenerationProperties;
import com.personalink.server.entity.ImageGenerationTaskEntity;
import com.personalink.server.mapper.ImageGenerationTaskMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ImageGenerationWorkerTest {
    private ImageGenerationTaskMapper mapper;
    private ImageProviderClient client;
    private GeneratedImageStorage storage;
    private ImageGenerationWorker worker;
    private ImageGenerationTaskEntity task;
    private final List<LambdaUpdateWrapper<ImageGenerationTaskEntity>> updates = new ArrayList<>();
    private final ImageProviderRoute route = new ImageProviderRoute(1, "saved-model", "https://saved.example", "region");

    @BeforeEach
    void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), ImageGenerationTaskEntity.class);
        this.mapper = mock(ImageGenerationTaskMapper.class);
        this.client = mock(ImageProviderClient.class);
        this.storage = mock(GeneratedImageStorage.class);
        ImageGenerationProperties properties = new ImageGenerationProperties();
        properties.setPollIntervalMillis(1000);
        properties.setTaskTimeoutSeconds(5);
        this.worker = new ImageGenerationWorker(this.mapper, this.client, this.storage, properties);
        this.task = new ImageGenerationTaskEntity();
        this.task.setId(7L);
        this.task.setDeleted(0);
        this.task.setProvider(this.route.provider());
        this.task.setModelName(this.route.modelName());
        this.task.setEndpoint(this.route.baseUrl());
        this.task.setRegion(this.route.region());
        this.task.setCoverPrompt("cover prompt");
        this.task.setDetailPrompt("detail prompt");
        this.task.setCoverSubmissionStarted(0);
        this.task.setDetailSubmissionStarted(0);
        when(this.mapper.selectById(7L)).thenReturn(this.task);
        when(this.mapper.update(isNull(), any())).thenAnswer(call -> {
            this.updates.add(call.getArgument(1));
            return 1;
        });
        when(this.client.submit(this.route, "cover prompt", 800, 800)).thenReturn("cover-task");
        when(this.client.submit(this.route, "detail prompt", 1100, 500)).thenReturn("detail-task");
        when(this.client.query(this.route, "cover-task")).thenReturn(new ImageProviderResult(2, "https://remote/cover", null));
        when(this.client.query(this.route, "detail-task")).thenReturn(new ImageProviderResult(2, "https://remote/detail", null));
        when(this.storage.store("https://remote/cover", 800, 800)).thenReturn("https://owned/cover");
        when(this.storage.store("https://remote/detail", 1100, 500)).thenReturn("https://owned/detail");
    }

    @Test
    void customResolutionReachesProviderAndStorage() {
        this.task.setCoverWidth(1024);
        this.task.setCoverHeight(1024);
        this.task.setDetailWidth(900);
        this.task.setDetailHeight(1200);
        when(this.client.submit(this.route, "cover prompt", 1024, 1024)).thenReturn("cover-task");
        when(this.client.submit(this.route, "detail prompt", 900, 1200)).thenReturn("detail-task");
        when(this.storage.store("https://remote/cover", 1024, 1024)).thenReturn("https://owned/cover");
        when(this.storage.store("https://remote/detail", 900, 1200)).thenReturn("https://owned/detail");
        this.worker.generateAsync(7L);
        verify(this.client).submit(this.route, "detail prompt", 900, 1200);
        verify(this.storage).store("https://remote/detail", 900, 1200);
        assertTrue(this.sets("task_status", 3));
    }

    @Test
    void failedCompareAndSetDoesNotSubmitOrQuery() {
        when(this.mapper.update(isNull(), any())).thenReturn(0);
        this.worker.generateAsync(7L);
        verifyNoInteractions(this.client, this.storage);
        verify(this.mapper, never()).selectById(anyLong());
    }

    @Test
    void submitsTwoTasksPersistsBothImagesAndCompletes() {
        this.worker.generateAsync(7L);
        verify(this.client).submit(this.route, "cover prompt", 800, 800);
        verify(this.client).submit(this.route, "detail prompt", 1100, 500);
        verify(this.client).query(this.route, "cover-task");
        verify(this.client).query(this.route, "detail-task");
        assertEquals("https://owned/cover", this.task.getCoverUrl());
        assertEquals("https://owned/detail", this.task.getDetailImageUrl());
        assertTrue(this.sets("cover_task_id", "cover-task"));
        assertTrue(this.sets("detail_task_id", "detail-task"));
        assertTrue(this.sets("task_status", 3));
    }

    @Test
    void existingCoverImageIsNotGeneratedOrDownloadedAgain() {
        this.task.setCoverUrl("https://owned/existing-cover");
        this.worker.generateAsync(7L);
        verify(this.client, never()).submit(any(), anyString(), eq(800), eq(800));
        verify(this.client, never()).query(this.route, "cover-task");
        verify(this.storage, never()).store(anyString(), eq(800), eq(800));
        assertEquals("https://owned/existing-cover", this.task.getCoverUrl());
        assertTrue(this.sets("task_status", 3));
    }

    @Test
    void transientQueryFailureOnlyRetriesQueryNotPaidSubmission() {
        this.task.setCoverTaskId("cover-task");
        this.task.setDetailTaskId("detail-task");
        when(this.client.query(this.route, "cover-task"))
                .thenThrow(new IllegalStateException("temporary query failure"))
                .thenReturn(new ImageProviderResult(2, "https://remote/cover", null));
        this.worker.generateAsync(7L);
        verify(this.client, never()).submit(any(), anyString(), anyInt(), anyInt());
        verify(this.client, times(2)).query(this.route, "cover-task");
        verify(this.client, times(1)).query(this.route, "detail-task");
        assertTrue(this.sets("task_status", 3));
    }

    @Test
    void unknownSubmissionKeepsStartedMarkerAndDoesNotResetForAnotherCharge() {
        when(this.client.submit(this.route, "cover prompt", 800, 800))
                .thenThrow(new ImageSubmissionUncertainException(new IllegalStateException("timeout")));
        this.worker.generateAsync(7L);
        assertTrue(this.sets("cover_submission_started", 1));
        assertFalse(this.sets("cover_submission_started", 0));
        assertTrue(this.sets("task_status", 4));
        verify(this.client, never()).submit(any(), anyString(), eq(1100), eq(500));
        verify(this.client, never()).query(any(), anyString());
    }

    @Test
    void terminalFailureClearsOnlyFailedUpstreamForExplicitRetry() {
        this.task.setCoverTaskId("cover-task");
        this.task.setDetailTaskId("detail-task");
        when(this.client.query(this.route, "cover-task")).thenReturn(new ImageProviderResult(3, null, "failed"));
        this.worker.generateAsync(7L);
        assertTrue(this.sets("cover_task_id", null));
        assertTrue(this.sets("cover_submission_started", 0));
        assertFalse(this.sets("detail_task_id", null));
        assertTrue(this.sets("task_status", 4));
        verify(this.client, never()).submit(any(), anyString(), anyInt(), anyInt());
    }

    @Test
    void restartRecoveryOnlyMarksInterruptedTasksAndKeepsPaidTaskIds() {
        this.worker.recoverInterruptedTasks();
        verifyNoInteractions(this.client, this.storage);
        assertEquals(1, this.updates.size());
        assertTrue(this.sets("task_status", 4));
        assertFalse(this.updates.get(0).getSqlSet().contains("task_id"));
        assertFalse(this.updates.get(0).getSqlSet().contains("submission_started"));
        assertFalse(this.updates.get(0).getSqlSet().contains("url"));
    }

    private boolean sets(String column, Object value) {
        return this.updates.stream().anyMatch(update -> update.getSqlSet().contains(column + "=")
                && update.getParamNameValuePairs().containsValue(value));
    }
}
