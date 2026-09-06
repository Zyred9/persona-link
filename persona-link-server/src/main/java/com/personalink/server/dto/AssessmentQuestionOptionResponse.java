package com.personalink.server.dto;

/**
 * 客户端选项，不包含计分信息。
 */
public record AssessmentQuestionOptionResponse(
        String optionId,
        String optionCode,
        String optionText) {
}
