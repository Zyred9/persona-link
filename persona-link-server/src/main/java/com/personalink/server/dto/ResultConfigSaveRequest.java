package com.personalink.server.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** 版本结果配置整体保存参数。 */
public record ResultConfigSaveRequest(@NotEmpty List<@Valid ResultTemplateSaveRequest> templates) {
}
