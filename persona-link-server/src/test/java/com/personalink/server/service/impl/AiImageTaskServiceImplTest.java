package com.personalink.server.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.personalink.server.ai.ImageGenerationWorker;
import com.personalink.server.ai.ImageProviderClient;
import com.personalink.server.ai.ImageProviderRoute;
import com.personalink.server.config.OssConfiguration;
import com.personalink.server.dto.ImageGenerationStartRequest;
import com.personalink.server.entity.ImageGenerationTaskEntity;
import com.personalink.server.entity.TestEntity;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.mapper.ImageGenerationTaskMapper;
import com.personalink.server.service.ContentService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiImageTaskServiceImplTest {
    private ContentService content;
    private ImageProviderClient provider;
    private ImageGenerationWorker worker;
    private ImageGenerationTaskMapper mapper;
    private AiImageTaskServiceImpl service;
    private ImageGenerationTaskEntity task;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), ImageGenerationTaskEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), TestEntity.class);
        this.content = mock(ContentService.class);
        this.provider = mock(ImageProviderClient.class);
        this.worker = mock(ImageGenerationWorker.class);
        this.mapper = mock(ImageGenerationTaskMapper.class);
        TransactionTemplate transaction = mock(TransactionTemplate.class);
        when(transaction.execute(any())).thenAnswer(call -> ((TransactionCallback<?>) call.getArgument(0)).doInTransaction(new SimpleTransactionStatus()));
        doAnswer(call -> {
            ((Consumer<org.springframework.transaction.TransactionStatus>) call.getArgument(0)).accept(new SimpleTransactionStatus());
            return null;
        }).when(transaction).executeWithoutResult(any());
        this.service = new AiImageTaskServiceImpl(this.content, this.provider, this.worker, transaction, mock(OssConfiguration.class));
        ReflectionTestUtils.setField(this.service, "baseMapper", this.mapper);
        TestEntity test = new TestEntity();
        test.setId(2L);
        test.setTestName("日常偏好");
        when(this.content.getOne(any(), eq(false))).thenReturn(test);
        this.task = new ImageGenerationTaskEntity();
        this.task.setId(8L);
        this.task.setTestId(2L);
        this.task.setDeleted(0);
        this.task.setProvider(1);
        this.task.setModelName("saved-model");
        this.task.setEndpoint("https://saved.example");
        this.task.setRegion("saved-region");
        this.task.setTaskStatus(3);
        this.task.setCoverUrl("https://owned.example/cover.png");
        this.task.setDetailImageUrl("https://owned.example/detail.png");
        when(this.mapper.selectById(8L)).thenReturn(this.task);
    }

    @Test
    void createSnapshotsProviderAndBothExactSizePrompts() {
        String prompt = "重点：生成的图片中不要包含文字\n职场沟通风格测试，封面图生成要贴合紫色+白色风格的图，详情图的风格无所谓！";
        when(this.provider.currentRoute()).thenReturn(new ImageProviderRoute(2, "model", "https://api.example", "region"));
        when(this.mapper.insert(any(ImageGenerationTaskEntity.class))).thenAnswer(call -> {
            ImageGenerationTaskEntity created = call.getArgument(0);
            created.setId(9L);
            when(this.mapper.selectById(9L)).thenReturn(created);
            assertEquals(2, created.getProvider());
            assertEquals("model", created.getModelName());
            assertTrue(created.getCoverPrompt().contains("800×800"));
            assertTrue(created.getCoverPrompt().contains("800:800"));
            assertTrue(created.getDetailPrompt().contains("1100×500"));
            assertTrue(created.getDetailPrompt().contains("1100:500"));
            assertEquals(prompt, created.getPromptText());
            assertTrue(created.getCoverPrompt().contains("本次只生成封面图，仅执行用户针对封面的要求和通用要求，不执行仅针对详情图的要求"));
            assertTrue(created.getDetailPrompt().contains("本次只生成详情图，仅执行用户针对详情图的要求和通用要求，不执行仅针对封面的要求"));
            for (String imagePrompt : new String[] {created.getCoverPrompt(), created.getDetailPrompt()}) {
                assertTrue(imagePrompt.contains(prompt));
                assertTrue(imagePrompt.contains("题型名称仅用于理解主题，不得自动作为文字绘制到画面中"));
                assertTrue(imagePrompt.contains("用户指定的风格和配色优先"));
                assertTrue(imagePrompt.contains("不套用另一张图的风格"));
                assertTrue(imagePrompt.contains("仅绘制纯插画"));
                assertTrue(imagePrompt.contains("不含标题、汉字、字母、数字、标签、对话框及伪文字"));
                assertTrue(imagePrompt.contains("纸张、书本和屏幕上的内容用无文字的色块表达"));
                assertTrue(imagePrompt.contains("服务商强制的 AI 生成标识除外"));
                assertFalse(imagePrompt.contains("统一温暖手绘风格"));
                // DTO 允许题型名称 100 字、用户提示词 1500 字；拼接后仍需满足供应商 2100 字限制。
                assertTrue(imagePrompt.length() - "日常偏好".length() - prompt.length() + 100 + 1500 <= 2100);
            }
            return 1;
        });
        assertEquals(9L, this.service.create(2L, this.request(prompt), 1L).id());
        verify(this.worker).generateAsync(9L);
    }

    @Test
    void customResolutionIsPersistedAndPartOfIdempotency() {
        when(this.provider.currentRoute()).thenReturn(new ImageProviderRoute(1, "model", "https://api.example", ""));
        when(this.mapper.insert(any(ImageGenerationTaskEntity.class))).thenAnswer(call -> {
            ImageGenerationTaskEntity created = call.getArgument(0);
            created.setId(9L);
            when(this.mapper.selectById(9L)).thenReturn(created);
            return 1;
        });
        var request = new ImageGenerationStartRequest(this.request("关键词").requestId(), "关键词", 1024, 1024, 900, 1200);
        var response = this.service.create(2L, request, 1L);
        assertEquals(1024, response.coverWidth());
        assertEquals(1200, response.detailHeight());
        ImageGenerationTaskEntity saved = this.mapper.selectById(9L);
        assertTrue(saved.getDetailPrompt().contains("900×1200"));
        assertFalse(saved.getDetailPrompt().contains("横向"));
        when(this.mapper.selectOne(any(), eq(false))).thenReturn(saved);
        assertThrows(BusinessException.class, () -> this.service.create(2L, this.request("关键词"), 1L));
    }

    @Test
    void jsonDimensionsRejectFractionsInsteadOfSilentlyTruncating() throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        String base = "{\"requestId\":\"" + this.request("关键词").requestId() + "\",\"promptText\":\"关键词\"";
        assertEquals(800, json.readValue(base + "}", ImageGenerationStartRequest.class).coverWidth());
        assertEquals(1024, json.readValue(base + ",\"coverWidth\":1024}", ImageGenerationStartRequest.class).coverWidth());
        for (String value : new String[] {"800.5", "\"800\"", "true", "2147483648"}) {
            assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,
                    () -> json.readValue(base + ",\"coverWidth\":" + value + "}", ImageGenerationStartRequest.class));
        }
    }

    @Test
    void resolutionValidationRejectsUnsafeSizesAndSupportsLegacyDefaults() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(this.request("关键词")).isEmpty());
            for (int[] size : new int[][] {{255, 1024}, {3000, 2000}, {256, 256}, {2048, 512}, {-1, 800},
                    {Integer.MAX_VALUE, Integer.MAX_VALUE}, {Integer.MAX_VALUE, 256}}) {
                assertFalse(validator.validate(new ImageGenerationStartRequest(this.request("关键词").requestId(),
                        "关键词", size[0], size[1], 1100, 500)).isEmpty());
            }
            assertTrue(validator.validate(new ImageGenerationStartRequest(this.request("关键词").requestId(),
                    "关键词", 512, 512, 1536, 512)).isEmpty());
            assertTrue(validator.validate(new ImageGenerationStartRequest(this.request("关键词").requestId(),
                    "关键词", 2000, 2000, 2200, 1000)).isEmpty());
        }
    }

    @Test
    void duplicateRequestDoesNotInsertOrChangeRoute() {
        this.task.setPromptText("关键词");
        when(this.mapper.selectOne(any(), eq(false))).thenReturn(this.task);
        assertEquals(8L, this.service.create(2L, this.request("关键词"), 1L).id());
        verify(this.mapper, never()).insert(any(ImageGenerationTaskEntity.class));
        verifyNoInteractions(this.provider);
        assertThrows(BusinessException.class, () -> this.service.create(2L, this.request("其他关键词"), 1L));
    }

    @Test
    void oneActiveTaskBlocksAnotherPaidSubmission() {
        when(this.mapper.selectCount(any())).thenReturn(1L);
        assertThrows(BusinessException.class, () -> this.service.create(2L, this.request("关键词"), 1L));
        verifyNoInteractions(this.provider, this.worker);
    }

    @Test
    void retryUsesPersistedProviderAndLeavesExistingImageIntact() {
        this.task.setTaskStatus(4);
        this.task.setDetailImageUrl(null);
        this.task.setDetailTaskId("upstream-id");
        this.task.setDetailSubmissionStarted(1);
        this.service.retry(8L);
        verify(this.provider).requireConfigured(new ImageProviderRoute(1, "saved-model", "https://saved.example", "saved-region"));
        verify(this.provider, never()).currentRoute();
        assertEquals("https://owned.example/cover.png", this.task.getCoverUrl());
        assertEquals("upstream-id", this.task.getDetailTaskId());
        verify(this.worker).generateAsync(8L);
    }

    @Test
    void uncertainSubmissionCannotBeRetriedAndChargedTwice() {
        this.task.setTaskStatus(4);
        this.task.setDetailImageUrl(null);
        this.task.setDetailSubmissionStarted(1);
        assertThrows(BusinessException.class, () -> this.service.retry(8L));
        verifyNoInteractions(this.provider, this.worker);
    }

    @Test
    void applyOnlyPassesImagesToDraftOperationAndIsIdempotent() {
        when(this.content.applyGeneratedImages(2L, this.task.getCoverUrl(), this.task.getDetailImageUrl(), 1L)).thenReturn(12L);
        this.service.apply(8L, 1L);
        verify(this.content).applyGeneratedImages(2L, this.task.getCoverUrl(), this.task.getDetailImageUrl(), 1L);
        this.task.setTaskStatus(5);
        this.service.apply(8L, 1L);
        verify(this.content, times(1)).applyGeneratedImages(anyLong(), anyString(), anyString(), anyLong());
    }

    @Test
    void olderTaskCannotOverwriteNewerAppliedResult() {
        when(this.mapper.selectCount(any())).thenReturn(1L);
        assertThrows(BusinessException.class, () -> this.service.apply(8L, 1L));
        verify(this.content, never()).applyGeneratedImages(anyLong(), anyString(), anyString(), anyLong());
    }

    @Test
    void deletedTaskCannotBeReadOrApplied() {
        this.task.setDeleted(1);
        assertThrows(BusinessException.class, () -> this.service.get(8L));
        assertThrows(BusinessException.class, () -> this.service.apply(8L, 1L));
    }

    private ImageGenerationStartRequest request(String prompt) {
        return new ImageGenerationStartRequest("d0605b4d-e6cb-43ec-bf54-227f01a84cce", prompt, null, null, null, null);
    }
}
