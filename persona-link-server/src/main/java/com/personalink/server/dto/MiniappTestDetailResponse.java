package com.personalink.server.dto;

/**
 * 小程序题型详情。
 *
 * @param testId 题型 ID
 * @param versionId 当前发布版本 ID
 * @param testType 测试类型：1单人，2双人
 * @param categoryId 分类 ID
 * @param categoryName 分类名称
 * @param title 当前发布版本标题
 * @param coverUrl 当前发布版本封面
 * @param detailImageUrl 当前发布版本详情整图，未配置时为空
 * @param questionCount 单次答题数量
 * @param estimatedMinutes 预计答题分钟数
 * @param description 题型说明
 */
public record MiniappTestDetailResponse(
        String testId,
        String versionId,
        Integer testType,
        String categoryId,
        String categoryName,
        String title,
        String coverUrl,
        String detailImageUrl,
        Integer questionCount,
        Integer estimatedMinutes,
        String description) {
}
