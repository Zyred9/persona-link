package com.personalink.server.enums;

import com.personalink.server.exception.BusinessException;

/**
 * 评测类型，同时作为答卷类型。
 */
public enum TestType {

    SINGLE(1, "单人测试"),
    PAIR(2, "双人测试");

    private final int code;
    private final String desc;

    TestType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return this.code;
    }

    public String getDesc() {
        return this.desc;
    }

    public static TestType of(int code) {
        for (TestType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new BusinessException(400, "评测类型不合法");
    }
}
