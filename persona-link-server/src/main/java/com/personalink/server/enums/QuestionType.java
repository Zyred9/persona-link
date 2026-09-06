package com.personalink.server.enums;

import com.personalink.server.exception.BusinessException;

/**
 * 题目类型。
 */
public enum QuestionType {

    SINGLE(1, "单选"),
    MULTIPLE(2, "多选");

    private final int code;
    private final String desc;

    QuestionType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return this.code;
    }

    public String getDesc() {
        return this.desc;
    }

    public static QuestionType of(int code) {
        for (QuestionType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new BusinessException(400, "题目类型不合法");
    }
}
