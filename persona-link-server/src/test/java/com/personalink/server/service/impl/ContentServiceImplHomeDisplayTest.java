package com.personalink.server.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.dto.TestResponse;
import com.personalink.server.dto.TestSaveRequest;
import com.personalink.server.entity.CategoryEntity;
import com.personalink.server.entity.ContentAuditLogEntity;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentServiceImplHomeDisplayTest {

    private TestMapper testMapper;
    private TestVersionMapper versionMapper;
    private CategoryService categoryService;
    private ContentServiceImpl contentService;
    private ContentAuditLogMapper auditMapper;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test"), TestEntity.class);
        this.testMapper = mock(TestMapper.class);
        this.versionMapper = mock(TestVersionMapper.class);
        this.categoryService = mock(CategoryService.class);
        this.auditMapper = mock(ContentAuditLogMapper.class);
        this.contentService = new ContentServiceImpl(
                this.versionMapper,
                mock(ScoreDimensionMapper.class),
                mock(QuestionMapper.class),
                mock(QuestionOptionMapper.class),
                mock(ResultTemplateMapper.class),
                this.auditMapper,
                this.categoryService,
                new ObjectMapper().findAndRegisterModules());
        ReflectionTestUtils.setField(this.contentService, "baseMapper", this.testMapper);
        ReflectionTestUtils.setField(this.contentService, "entityClass", TestEntity.class);
    }

    @Test
    void settingFocusShouldClearPreviousFocusAndUpdateTarget() {
        TestEntity target = new TestEntity();
        target.setId(20L);
        target.setCategoryId(12L);
        target.setStatus(1);
        CategoryEntity category = new CategoryEntity();
        category.setStatus(1);
        TestResponse response = new TestResponse();
        response.setId(20L);
        response.setHomeDisplay(1);

        when(this.testMapper.selectById(20L)).thenReturn(target);
        when(this.categoryService.getById(12L)).thenReturn(category);
        when(this.versionMapper.selectCount(any())).thenReturn(1L);
        when(this.testMapper.selectTestDetail(20L)).thenReturn(response);
        TestEntity previous = new TestEntity();
        previous.setId(19L);
        previous.setHomeDisplay(1);
        previous.setHomeSort(90);
        when(this.testMapper.selectList(any())).thenReturn(List.of(previous));

        TestResponse result = this.contentService.updateTestHomeDisplay(20L, 1, 80, 1L);

        assertEquals(1, result.getHomeDisplay());
        verify(this.testMapper, times(2)).update(eq(null), any());
        ArgumentCaptor<java.util.Collection<ContentAuditLogEntity>> audits = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(this.auditMapper).insert(audits.capture());
        ContentAuditLogEntity removedFocus = audits.getValue().iterator().next();
        assertEquals(19L, removedFocus.getBizId());
        assertTrue(removedFocus.getBeforeSnapshot().contains("\"homeDisplay\":1"));
        assertTrue(removedFocus.getAfterSnapshot().contains("\"homeDisplay\":0"));
        assertTrue(removedFocus.getReason().contains("20"));
    }

    @Test
    void publishedTypeCannotChangeBetweenSingleAndPair() {
        TestEntity original = new TestEntity();
        original.setId(20L);
        original.setTestType(2);
        CategoryEntity category = new CategoryEntity();
        category.setStatus(1);
        when(this.testMapper.selectOne(any())).thenReturn(original);
        when(this.categoryService.getById(12L)).thenReturn(category);
        when(this.versionMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> this.contentService.updateTest(20L,
                new TestSaveRequest("名称", 1, 12L, 1), 1L));
        verify(this.testMapper, never()).updateById(any(TestEntity.class));
    }

    @Test
    void editingDisabledTestClearsPreviousHomePosition() {
        TestEntity original = new TestEntity();
        original.setId(20L);
        original.setTestType(2);
        CategoryEntity category = new CategoryEntity();
        category.setStatus(1);
        when(this.testMapper.selectOne(any())).thenReturn(original);
        when(this.categoryService.getById(12L)).thenReturn(category);
        when(this.testMapper.selectTestDetail(20L)).thenReturn(new TestResponse());

        this.contentService.updateTest(20L, new TestSaveRequest("名称", 2, 12L, 0), 1L);

        ArgumentCaptor<TestEntity> saved = ArgumentCaptor.forClass(TestEntity.class);
        verify(this.testMapper).updateById(saved.capture());
        assertEquals(0, saved.getValue().getHomeDisplay());
        assertEquals(0, saved.getValue().getHomeSort());
    }

    @Test
    void scheduledPublishingSkipsDisabledTestWithoutChangingVersions() {
        TestVersionEntity pending = new TestVersionEntity();
        pending.setTestId(20L);
        TestEntity disabled = new TestEntity();
        disabled.setId(20L);
        disabled.setCategoryId(12L);
        disabled.setStatus(0);
        when(this.versionMapper.selectList(any())).thenReturn(List.of(pending));
        when(this.testMapper.selectList(any())).thenReturn(List.of(disabled));
        when(this.categoryService.listByIds(any())).thenReturn(List.of());

        assertEquals(0, this.contentService.publishDueVersions());
        verify(this.versionMapper, never()).updateById(any(TestVersionEntity.class));
        verify(this.versionMapper, never()).updateById(any(java.util.Collection.class));
    }

    @Test
    void scheduledReplacementAuditsBothNewAndOldVersions() {
        TestEntity test = new TestEntity();
        test.setId(20L);
        test.setCategoryId(12L);
        test.setStatus(1);
        CategoryEntity category = new CategoryEntity();
        category.setId(12L);
        category.setStatus(1);
        TestVersionEntity pending = new TestVersionEntity();
        pending.setId(30L);
        pending.setTestId(20L);
        pending.setVersionStatus(3);
        TestVersionEntity published = new TestVersionEntity();
        published.setId(29L);
        published.setTestId(20L);
        published.setVersionStatus(4);
        when(this.testMapper.selectList(any())).thenReturn(List.of(test));
        when(this.categoryService.listByIds(any())).thenReturn(List.of(category));
        when(this.versionMapper.selectList(any())).thenReturn(List.of(pending), List.of(pending), List.of(published));

        assertEquals(1, this.contentService.publishDueVersions());

        ArgumentCaptor<java.util.Collection<ContentAuditLogEntity>> audits = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(this.auditMapper).insert(audits.capture());
        assertEquals(2, audits.getValue().size());
        ContentAuditLogEntity offline = audits.getValue().stream()
                .filter(audit -> Long.valueOf(29L).equals(audit.getBizId())).findFirst().orElseThrow();
        assertEquals(5, offline.getActionType());
        assertTrue(offline.getBeforeSnapshot().contains("\"versionStatus\":4"));
        assertTrue(offline.getAfterSnapshot().contains("\"versionStatus\":5"));
    }
}
