package com.personalink.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 后台账号重置密码参数。 */
public record AdminPasswordResetRequest(@NotBlank @Size(min = 8, max = 72) String password) {
}
