package com.personalink.server.dto;

import java.util.List;

/** 题目详情响应。 */
public record QuestionResponse(
        Long id,
        Long versionId,
        Integer questionType,
        Long dimensionId,
        Integer minSelectCount,
        Integer maxSelectCount,
        Integer questionNo,
        String questionText,
        Integer requiredFlag,
        Integer sortNo,
        List<QuestionOptionResponse> options) {
}
