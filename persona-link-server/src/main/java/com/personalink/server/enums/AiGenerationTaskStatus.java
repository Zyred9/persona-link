package com.personalink.server.enums;

import com.personalink.server.exception.BusinessException;

/** AI 题库生成任务状态。 */
public enum AiGenerationTaskStatus {

    PENDING(1, "待生成"),
    GENERATING(2, "生成中"),
    PENDING_REVIEW(3, "待人工审核"),
    FAILED(4, "生成失败"),
    SUBMITTED(5, "已提交");

    private final int code;
    private final String desc;

    AiGenerationTaskStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return this.code;
    }

    public String getDesc() {
        return this.desc;
    }

    public static AiGenerationTaskStatus of(int code) {
        for (AiGenerationTaskStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new BusinessException(400, "AI 生成任务状态不合法");
    }
}
