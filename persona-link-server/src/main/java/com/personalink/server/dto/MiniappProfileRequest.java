package com.personalink.server.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 用户资料；空串表示清空对应字段，不接收用户身份。 */
public record MiniappProfileRequest(
        @NotNull @Size(max = 32) String nickname,
        @NotNull @Size(max = 1024) String avatarUrl) {
}
