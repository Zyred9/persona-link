package com.personalink.server.dto;

import java.util.List;

/** DeepSeek 一次生成的维度和结果规则。 */
public record AiGeneratedSetup(
        List<AiGeneratedDimension> dimensions,
        List<AiGeneratedResultRule> resultRules) {
}
