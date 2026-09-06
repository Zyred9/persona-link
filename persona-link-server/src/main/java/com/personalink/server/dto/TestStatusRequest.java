package com.personalink.server.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 题型状态更新参数。 */
public record TestStatusRequest(@NotNull @Min(0) @Max(1) Integer status) {
}
