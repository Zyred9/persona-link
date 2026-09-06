package com.personalink.server.dto;

import java.util.List;

/**
 * 小程序首页响应。
 *
 * @param categories 已启用分类
 * @param focusTests 焦点推荐题型
 * @param recommendedTests 推荐题型
 * @param allTests 全部可用题型
 */
public record MiniappHomeResponse(
        List<MiniappHomeCategoryResponse> categories,
        List<MiniappHomeTestResponse> focusTests,
        List<MiniappHomeTestResponse> recommendedTests,
        List<MiniappHomeTestResponse> allTests) {
}
