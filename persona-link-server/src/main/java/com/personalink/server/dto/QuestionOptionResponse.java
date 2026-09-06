package com.personalink.server.dto;

/** 题目选项响应。 */
public record QuestionOptionResponse(
        Long id, String optionCode, String optionText, Long dimensionId,
        Integer scoreValue, Integer sortNo) {
}
