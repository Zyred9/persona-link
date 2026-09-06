package com.personalink.server.dto;

import java.util.List;

/** 版本结果配置响应。 */
public record ResultConfigResponse(
        Long versionId,
        List<ScoreDimensionResponse> dimensions,
        List<ResultTemplateResponse> templates) {
}
