package com.personalink.server.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.personalink.server.dto.CategoryResponse;
import com.personalink.server.dto.CategorySaveRequest;
import com.personalink.server.entity.CategoryEntity;
import com.personalink.server.entity.TestEntity;
import com.personalink.server.mapper.CategoryMapper;
import com.personalink.server.mapper.TestMapper;
import com.personalink.server.exception.BusinessException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryServiceImplTest {

    private CategoryMapper categoryMapper;
    private TestMapper testMapper;
    private CategoryServiceImpl categoryService;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant builderAssistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(builderAssistant, CategoryEntity.class);
        TableInfoHelper.initTableInfo(builderAssistant, TestEntity.class);
        this.categoryMapper = mock(CategoryMapper.class);
        this.testMapper = mock(TestMapper.class);
        this.categoryService = new CategoryServiceImpl(this.testMapper);
        ReflectionTestUtils.setField(this.categoryService, "baseMapper", this.categoryMapper);
        ReflectionTestUtils.setField(this.categoryService, "entityClass", CategoryEntity.class);
    }

    @Test
    void createShouldAppendHighestSortNo() {
        CategoryEntity lastCategory = new CategoryEntity();
        lastCategory.setSortNo(5);
        when(this.categoryMapper.selectOne(any())).thenReturn(lastCategory);
        when(this.categoryMapper.insert(any(CategoryEntity.class))).thenAnswer(invocation -> {
            CategoryEntity entity = invocation.getArgument(0);
            entity.setId(9L);
            return 1;
        });
        CategoryResponse response = new CategoryResponse();
        response.setId(9L);
        when(this.categoryMapper.selectCategoryById(9L)).thenReturn(response);

        this.categoryService.create(new CategorySaveRequest("情绪探索", 1));

        ArgumentCaptor<CategoryEntity> captor = ArgumentCaptor.forClass(CategoryEntity.class);
        verify(this.categoryMapper).insert(captor.capture());
        CategoryEntity saved = captor.getValue();
        assertEquals("情绪探索", saved.getCategoryName());
        assertEquals(6, saved.getSortNo());
    }

    @Test
    void deleteShouldRejectCategoriesWithActiveTests() {
        List<Long> ids = List.of(1L, 2L);
        when(this.testMapper.selectCount(any())).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> this.categoryService.deleteByIds(ids));

        assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
        verify(this.testMapper).selectCount(any());
    }

    @Test
    void updateOrderShouldRejectMissingCategory() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> this.categoryService.updateOrder(List.of(1L, 2L)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }
}
