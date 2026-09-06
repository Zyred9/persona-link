package com.personalink.server.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 分类排序参数。
 *
 * @param orderedIds 按页面从前到后排列的全部分类 ID
 */
public record CategoryOrderRequest(
        @NotEmpty List<@NotNull @Positive Long> orderedIds) {
}
