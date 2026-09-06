package com.personalink.server.dto;

/**
 * 小程序首页分类。
 *
 * @param categoryId 分类 ID
 * @param categoryName 分类名称
 */
public record MiniappHomeCategoryResponse(
        String categoryId,
        String categoryName) {
}
