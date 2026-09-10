package com.personalink.server.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 通用配置分页查询。 */
@Getter
@Setter
public class AppConfigQuery {
    @Size(max = 128)
    private String keyword;
    @Min(1) @Max(1000000)
    private long page = 1;
    @Min(1) @Max(100)
    private long size = 20;
}
