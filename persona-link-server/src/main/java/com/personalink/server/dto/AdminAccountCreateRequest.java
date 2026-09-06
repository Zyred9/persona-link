package com.personalink.server.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 后台账号新增参数。 */
public record AdminAccountCreateRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 64) String displayName,
        @NotNull @Min(1) @Max(3) Integer roleType,
        @NotNull @Min(0) @Max(1) Integer status) {
}
