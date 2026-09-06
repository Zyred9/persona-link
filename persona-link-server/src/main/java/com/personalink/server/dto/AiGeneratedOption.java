package com.personalink.server.dto;

/** DeepSeek 生成的题目选项。 */
public record AiGeneratedOption(
        String optionCode,
        String optionText,
        String dimensionCode,
        Integer scoreValue,
        Integer sortNo) {
}
