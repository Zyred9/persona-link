package com.personalink.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

/** 小程序按约定的公开配置键批量读取配置值。 */
@Getter
@Setter
public class MiniappConfigQuery {
    /** 一次最多读取 20 个公开键。 */
    @NotEmpty
    @Size(max = 20)
    private List<@NotBlank @Size(max = 128) String> keys;
}
