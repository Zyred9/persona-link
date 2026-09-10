package com.personalink.server.dto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Objects;

/** 通用配置写入参数；已知业务配置沿用各自约束。 */
public record AppConfigSaveRequest(
        @NotBlank @Size(max = 128) @Pattern(regexp = "[a-z][a-z0-9._-]*") String configKey,
        @NotNull @Size(max = 65535) String configValue,
        @NotNull @Min(1) @Max(4) Integer valueType,
        @NotBlank @Size(max = 100) String configName,
        @Size(max = 500) String remark) {

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public AppConfigSaveRequest {
        configName = Objects.isNull(configName) ? null : configName.trim();
        remark = Objects.isNull(remark) ? "" : remark.trim();
        if ("miniapp.home.title_image_url".equals(configKey)) {
            configValue = new HomeConfigSaveRequest(configValue).titleImageUrl();
        } else if ("miniapp.version".equals(configKey) && Objects.nonNull(configValue)) {
            configValue = configValue.trim();
        }
    }

    @AssertTrue(message = "配置值与类型不匹配，数字须为有效数字，布尔须为true/false，JSON须为完整JSON")
    public boolean isValueValid() {
        if (Objects.isNull(configValue) || Objects.isNull(valueType)) { return true; }
        try {
            return switch (valueType) {
                case 1 -> true;
                case 2 -> { new BigDecimal(configValue); yield true; }
                case 3 -> "true".equals(configValue) || "false".equals(configValue);
                case 4 -> {
                    var node = JSON.readTree(configValue);
                    yield Objects.nonNull(node) && !node.isMissingNode();
                }
                default -> false;
            };
        } catch (Exception exception) {
            return false;
        }
    }

    @AssertTrue(message = "首页标题图必须为字符串类型的有效图片地址，版本号必须为1至32字符的非空字符串")
    public boolean isBusinessValueValid() {
        if ("miniapp.home.title_image_url".equals(configKey)) {
            return Integer.valueOf(1).equals(valueType) && configValue.length() <= 1024
                    && new HomeConfigSaveRequest(configValue).isTitleImageUrlValid();
        }
        if ("miniapp.version".equals(configKey)) {
            return Integer.valueOf(1).equals(valueType) && Objects.nonNull(configValue)
                    && !configValue.isBlank() && configValue.length() <= 32;
        }
        return true;
    }
}
