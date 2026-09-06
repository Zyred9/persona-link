package com.personalink.server.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 题型首页展示配置参数。 */
public record TestHomeDisplayRequest(
        @NotNull @Min(0) @Max(2) Integer homeDisplay,
        @NotNull @Min(0) Integer homeSort) {
}
