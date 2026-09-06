package com.personalink.server.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.dto.TestResponse;
import com.personalink.server.entity.CategoryEntity;
import com.personalink.server.entity.TestEntity;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentServiceImplHomeDisplayTest {

    private TestMapper testMapper;
    private TestVersionMapper versionMapper;
    private CategoryService categoryService;
    private ContentServiceImpl contentService;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test"), TestEntity.class);
        this.testMapper = mock(TestMapper.class);
        this.versionMapper = mock(TestVersionMapper.class);
        this.categoryService = mock(CategoryService.class);
        this.contentService = new ContentServiceImpl(
                this.versionMapper,
                mock(ScoreDimensionMapper.class),
                mock(QuestionMapper.class),
                mock(QuestionOptionMapper.class),
                mock(ResultTemplateMapper.class),
                mock(ContentAuditLogMapper.class),
                this.categoryService,
                new ObjectMapper());
        ReflectionTestUtils.setField(this.contentService, "baseMapper", this.testMapper);
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

        TestResponse result = this.contentService.updateTestHomeDisplay(20L, 1, 80, 1L);

        assertEquals(1, result.getHomeDisplay());
        verify(this.testMapper, times(2)).update(eq(null), any());
    }
}
