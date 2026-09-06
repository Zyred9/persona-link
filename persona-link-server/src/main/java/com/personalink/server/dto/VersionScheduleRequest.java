package com.personalink.server.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** 题型版本排期参数。 */
public record VersionScheduleRequest(
        @NotNull @Future LocalDateTime scheduledAt) {
}
