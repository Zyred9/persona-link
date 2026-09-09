package com.personalink.server.enums;
/** 广告报告领域数字枚举。 */
public enum ReportKind {
    SINGLE(1, "单人报告"),
    PAIR(2, "双人报告");
    private final int code;
    private final String desc;
    ReportKind(int code, String desc) { this.code = code; this.desc = desc; }
    public int getCode() { return this.code; }
    public String getDesc() { return this.desc; }
    public static ReportKind of(int code) {
        for (ReportKind value : values()) { if (value.code == code) { return value; } }
        throw new IllegalArgumentException("无效ReportKind编码：" + code);
    }
}
