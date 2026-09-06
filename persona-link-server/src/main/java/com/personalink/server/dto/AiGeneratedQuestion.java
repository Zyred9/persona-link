package com.personalink.server.dto;

import java.util.List;

/** DeepSeek 生成的题目。 */
public record AiGeneratedQuestion(
        Integer questionType,
        String dimensionCode,
        Integer minSelectCount,
        Integer maxSelectCount,
        Integer questionNo,
        String questionText,
        Integer requiredFlag,
        Integer sortNo,
        List<AiGeneratedOption> options) {
}
