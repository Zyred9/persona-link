package com.personalink.server.enums;

import com.personalink.server.exception.BusinessException;

/**
 * 答卷状态。
 */
public enum AnswerStatus {

    IN_PROGRESS(1, "作答中"),
    SUBMITTED(2, "已提交"),
    REPORT_READY(3, "报告已生成"),
    ABANDONED(4, "已放弃");

    private final int code;
    private final String desc;

    AnswerStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return this.code;
    }

    public String getDesc() {
        return this.desc;
    }

    public static AnswerStatus of(int code) {
        for (AnswerStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new BusinessException(400, "答卷状态不合法");
    }
}
