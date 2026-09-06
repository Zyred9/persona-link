package com.personalink.server.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/** 按题保存答案请求。 */
public record SaveAnswerRequest(
        @NotBlank @Pattern(regexp = "[1-9]\\d{0,19}") String questionId,
        @NotEmpty List<@NotBlank @Pattern(regexp = "[1-9]\\d{0,19}") String> optionIds) {
}
