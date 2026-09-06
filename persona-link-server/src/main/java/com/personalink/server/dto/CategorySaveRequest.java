package com.personalink.server.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 分类保存参数。
 *
 * @param categoryName 分类名称
 * @param status 状态：1启用，0禁用
 */
public record CategorySaveRequest(
        @NotBlank @Size(max = 64) String categoryName,
        @NotNull @Min(0) @Max(1) Integer status) {
}
