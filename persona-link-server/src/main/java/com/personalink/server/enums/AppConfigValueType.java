package com.personalink.server.enums;

import com.personalink.server.exception.BusinessException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 通用配置值的存储类型。 */
@Getter
@RequiredArgsConstructor
public enum AppConfigValueType {
    STRING(1, "字符串"), NUMBER(2, "数字"), BOOLEAN(3, "布尔"), JSON(4, "JSON");
    private final int code;
    private final String desc;

    public static AppConfigValueType of(int code) {
        for (AppConfigValueType value : values()) {
            if (value.code == code) { return value; }
        }
        throw new BusinessException(400, "配置值类型错误");
    }
}
