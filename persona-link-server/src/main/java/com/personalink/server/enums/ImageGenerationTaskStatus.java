package com.personalink.server.enums;

import com.personalink.server.exception.BusinessException;

/** 双图生成任务状态。 */
public enum ImageGenerationTaskStatus {
    PENDING(1, "排队中"), GENERATING(2, "生成中"), SUCCEEDED(3, "生成成功"),
    FAILED(4, "生成失败"), APPLIED(5, "已采用");

    private final int code;
    private final String desc;
    ImageGenerationTaskStatus(int code, String desc) { this.code = code; this.desc = desc; }
    public int getCode() { return this.code; }
    public String getDesc() { return this.desc; }
    public static ImageGenerationTaskStatus of(int code) {
        for (ImageGenerationTaskStatus value : values()) {
            if (value.code == code) { return value; }
        }
        throw new BusinessException(400, "AI 生图任务状态不合法");
    }
}

