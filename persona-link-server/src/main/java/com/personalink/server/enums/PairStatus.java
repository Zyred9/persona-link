package com.personalink.server.enums;

import com.personalink.server.exception.BusinessException;

/**
 * 双人配对状态。
 */
public enum PairStatus {

    INITIATOR_DONE(1, "发起者已完成"),
    PARTNER_JOINED(2, "对方已加入"),
    BOTH_DONE(3, "双方已完成"),
    REPORT_READY(4, "报告已生成"),
    EXPIRED(5, "已过期"),
    CANCELLED(6, "已取消");

    private final int code;
    private final String desc;

    PairStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return this.code;
    }

    public String getDesc() {
        return this.desc;
    }

    public static PairStatus of(int code) {
        for (PairStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new BusinessException(400, "配对状态不合法");
    }
}
