package com.personalink.server.service.impl;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.config.OssConfiguration;
import com.personalink.server.dto.ScoreDimensionSaveRequest;
import com.personalink.server.dto.TestVersionResponse;
import com.personalink.server.dto.TestVersionSaveRequest;
import com.personalink.server.entity.QuestionEntity;
import com.personalink.server.entity.ResultTemplateEntity;
import com.personalink.server.entity.ScoreDimensionEntity;
import com.personalink.server.entity.TestEntity;
import com.personalink.server.entity.TestVersionEntity;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.mapper.ContentAuditLogMapper;
import com.personalink.server.mapper.QuestionMapper;
import com.personalink.server.mapper.QuestionOptionMapper;
import com.personalink.server.mapper.ResultTemplateMapper;
import com.personalink.server.mapper.ScoreDimensionMapper;
import com.personalink.server.mapper.TestMapper;
import com.personalink.server.mapper.TestVersionMapper;
import com.personalink.server.service.CategoryService;
import jakarta.validation.Validation;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentServiceImplDetailImageTest {

    private TestMapper testMapper;
    private TestVersionMapper versionMapper;
    private ContentServiceImpl service;

    @BeforeEach
    void setUp() {
        for (Class<?> entity : List.of(TestEntity.class, TestVersionEntity.class, ScoreDimensionEntity.class,
                QuestionEntity.class, ResultTemplateEntity.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), entity);
        }
        this.testMapper = mock(TestMapper.class);
        this.versionMapper = mock(TestVersionMapper.class);
        this.service = new ContentServiceImpl(this.versionMapper, mock(ScoreDimensionMapper.class),
                mock(QuestionMapper.class), mock(QuestionOptionMapper.class), mock(ResultTemplateMapper.class),
                mock(ContentAuditLogMapper.class), mock(CategoryService.class), new ObjectMapper());
        ReflectionTestUtils.setField(this.service, "baseMapper", this.testMapper);
        ReflectionTestUtils.setField(this.service, "entityClass", TestEntity.class);
    }

    @Test
    void createShouldPersistIndependentDetailImageAndReturnIt() throws Exception {
        when(this.testMapper.selectOne(any())).thenReturn(new TestEntity());
        when(this.versionMapper.insert(any(TestVersionEntity.class))).thenAnswer(invocation -> {
            TestVersionEntity saved = invocation.getArgument(0);
            saved.setId(12L);
            when(this.versionMapper.selectById(12L)).thenReturn(saved);
            return 1;
        });

        TestVersionResponse response = this.service.createVersion(2L, this.request(" /uploads/detail.png "), 1L);

        assertEquals("/uploads/cover.png", response.coverUrl());
        assertEquals("/uploads/detail.png", response.detailImageUrl());
        assertEquals("/uploads/detail.png", new ObjectMapper().valueToTree(response).get("detailImageUrl").asText());
        ArgumentCaptor<TestVersionEntity> saved = ArgumentCaptor.forClass(TestVersionEntity.class);
        verify(this.versionMapper).insert(saved.capture());
        assertEquals("/uploads/detail.png", saved.getValue().getDetailImageUrl());
    }

    @Test
    void updateShouldAllowClearingDetailImageWithoutChangingCover() throws NoSuchFieldException {
        TestVersionEntity draft = this.version(1);
        when(this.versionMapper.selectOne(any(), eq(false))).thenReturn(draft);
        when(this.versionMapper.selectById(12L)).thenReturn(draft);

        this.service.updateVersion(12L, this.request("   "), 1L);

        ArgumentCaptor<TestVersionEntity> update = ArgumentCaptor.forClass(TestVersionEntity.class);
        verify(this.versionMapper).updateById(update.capture());
        assertNull(update.getValue().getDetailImageUrl());
        assertEquals("/uploads/cover.png", update.getValue().getCoverUrl());
        assertEquals(FieldStrategy.ALWAYS, TestVersionEntity.class.getDeclaredField("detailImageUrl")
                .getAnnotation(TableField.class).updateStrategy());
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 6})
    void copyingHistoricalVersionShouldRetainIndependentDetailImage(int status) {
        TestVersionEntity published = this.version(status);
        when(this.versionMapper.selectById(12L)).thenReturn(published);
        when(this.versionMapper.selectOne(any(), eq(false))).thenReturn(published, null, published);
        when(this.testMapper.selectOne(any())).thenReturn(new TestEntity());
        when(this.versionMapper.insert(any(TestVersionEntity.class))).thenAnswer(invocation -> {
            TestVersionEntity saved = invocation.getArgument(0);
            saved.setId(13L);
            when(this.versionMapper.selectById(13L)).thenReturn(saved);
            return 1;
        });

        TestVersionResponse draft = this.service.copyVersionAsDraft(12L, 1L);

        assertEquals(1, draft.versionStatus());
        assertEquals("/uploads/cover.png", draft.coverUrl());
        assertEquals("/uploads/detail.png", draft.detailImageUrl());
        assertEquals(status, published.getVersionStatus());
        var locks = org.mockito.Mockito.inOrder(this.testMapper, this.versionMapper);
        locks.verify(this.versionMapper).selectById(12L);
        locks.verify(this.testMapper).selectOne(any());
        locks.verify(this.versionMapper, org.mockito.Mockito.times(3)).selectOne(any(), eq(false));
    }

    @Test
    void detailImageShouldBeOptionalButRejectOversizedUrls() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(this.request(null)).isEmpty());
            assertTrue(validator.validate(this.request("x".repeat(501))).stream()
                    .anyMatch(violation -> "detailImageUrl".equals(violation.getPropertyPath().toString())));
        }
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void generatedImagesOnlyUpdateTwoColumnsOfExistingDraft() {
        TestVersionEntity draft = this.version(1);
        when(this.testMapper.selectOne(any())).thenReturn(new TestEntity());
        when(this.versionMapper.selectOne(any(), eq(false))).thenReturn(draft);
        when(this.versionMapper.selectById(12L)).thenReturn(draft);
        when(this.versionMapper.update(org.mockito.ArgumentMatchers.isNull(), any())).thenReturn(1);

        assertEquals(12L, this.service.applyGeneratedImages(2L,
                "/uploads/new-cover.png", "/uploads/new-detail.png", 1L));

        ArgumentCaptor<LambdaUpdateWrapper> update = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(this.versionMapper).update(org.mockito.ArgumentMatchers.isNull(), update.capture());
        assertTrue(update.getValue().getSqlSet().contains("cover_url="));
        assertTrue(update.getValue().getSqlSet().contains("detail_image_url="));
        assertEquals(2, update.getValue().getSqlSet().split(",").length);
        assertTrue(update.getValue().getSqlSegment().contains("version_status"));
        verify(this.versionMapper, never()).updateById(any(TestVersionEntity.class));
        assertEquals(1, draft.getVersionStatus());
    }

    @Test
    void materialReferencedOnlyByDetailImageShouldNotBeDeleted(@TempDir Path directory) throws Exception {
        LocalAssetService assets = new LocalAssetService(directory.toString(), this.versionMapper,
                new OssConfiguration("", "", "", "", ""), null);
        Files.write(directory.resolve("detail.png"), new byte[]{1, 2, 3});
        String url = "/uploads/detail.png";
        TestVersionEntity version = this.version(1);
        version.setDetailImageUrl(url);
        when(this.versionMapper.selectList(any())).thenReturn(List.of(version));
        String fileName = url.substring("/uploads/".length());

        assertEquals(1L, assets.listImages(null).get(0).referenceCount());
        assertThrows(BusinessException.class, () -> assets.deleteImage(fileName));
        assertTrue(Files.exists(directory.resolve(fileName)));
    }

    private TestVersionSaveRequest request(String detailImageUrl) {
        return new TestVersionSaveRequest("测试", "/uploads/cover.png", detailImageUrl, "说明", 3, 5, null,
                List.of(new ScoreDimensionSaveRequest(null, "score", "维度", 0)));
    }

    private TestVersionEntity version(int status) {
        TestVersionEntity version = new TestVersionEntity();
        version.setId(12L);
        version.setTestId(2L);
        version.setVersionNo(1);
        version.setVersionStatus(status);
        version.setCoverUrl("/uploads/cover.png");
        version.setDetailImageUrl("/uploads/detail.png");
        return version;
    }
}
