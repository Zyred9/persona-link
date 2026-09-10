package com.personalink.server.service.impl;

import com.personalink.server.dto.MiniappHomeCategoryResponse;
import com.personalink.server.dto.MiniappHomeResponse;
import com.personalink.server.dto.MiniappHomeTestResponse;
import com.personalink.server.mapper.MiniappContentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MiniappContentServiceImplTest {

    private MiniappContentMapper contentMapper;
    private MiniappContentServiceImpl contentService;

    @BeforeEach
    void setUp() {
        this.contentMapper = mock(MiniappContentMapper.class);
        var appConfigService = mock(com.personalink.server.service.AppConfigService.class);
        when(appConfigService.readHomeConfig()).thenReturn(new com.personalink.server.dto.HomeConfigResponse("https://example.com/title.png"));
        this.contentService = new MiniappContentServiceImpl(appConfigService);
        ReflectionTestUtils.setField(this.contentService, "baseMapper", this.contentMapper);
    }

    @Test
    void getHomeShouldExcludeConfiguredTestsFromCategoryTests() {
        MiniappHomeCategoryResponse category = new MiniappHomeCategoryResponse("1", "职场协作");
        MiniappHomeTestResponse focus = this.test("10", "1");
        MiniappHomeTestResponse recommended = this.test("11", "1");
        MiniappHomeTestResponse another = this.test("12", "2");

        when(this.contentMapper.selectHomeCategories()).thenReturn(List.of(category));
        when(this.contentMapper.selectHomeTests(0)).thenReturn(List.of(another));
        when(this.contentMapper.selectHomeTests(1)).thenReturn(List.of(focus));
        when(this.contentMapper.selectHomeTests(2)).thenReturn(List.of(recommended));

        MiniappHomeResponse response = this.contentService.getHome();

        assertEquals(List.of(focus), response.focusTests());
        assertEquals("https://example.com/title.png", response.titleImageUrl());
        assertEquals(List.of(recommended), response.recommendedTests());
        assertEquals(List.of(another), response.allTests());
    }

    @Test
    void getHomeShouldReturnEnabledCategoriesWithoutConfiguredRecommendations() {
        MiniappHomeCategoryResponse category = new MiniappHomeCategoryResponse("15", "测试测试");
        when(this.contentMapper.selectHomeCategories()).thenReturn(List.of(category));
        when(this.contentMapper.selectHomeTests(0)).thenReturn(List.of());
        when(this.contentMapper.selectHomeTests(1)).thenReturn(List.of());
        when(this.contentMapper.selectHomeTests(2)).thenReturn(List.of());

        MiniappHomeResponse response = this.contentService.getHome();

        assertEquals(List.of(category), response.categories());
    }

    private MiniappHomeTestResponse test(String testId, String categoryId) {
        return new MiniappHomeTestResponse(testId, testId, 1, categoryId, "分类",
                "测试" + testId, null, 20, 3);
    }
}
