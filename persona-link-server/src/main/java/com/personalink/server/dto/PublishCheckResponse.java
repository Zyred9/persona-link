package com.personalink.server.dto;

import java.util.List;

/** 发布前检查结果。 */
public record PublishCheckResponse(boolean passed, List<String> errors) {
}
