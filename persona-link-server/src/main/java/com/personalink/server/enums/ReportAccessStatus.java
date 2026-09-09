package com.personalink.server.enums;
/** 广告报告领域数字枚举。 */
public enum ReportAccessStatus {
    ALLOWED(1, "可查看"),
    AD_REQUIRED(2, "需要观看广告");
    private final int code;
    private final String desc;
    ReportAccessStatus(int code, String desc) { this.code = code; this.desc = desc; }
    public int getCode() { return this.code; }
    public String getDesc() { return this.desc; }
    public static ReportAccessStatus of(int code) {
        for (ReportAccessStatus value : values()) { if (value.code == code) { return value; } }
        throw new IllegalArgumentException("无效ReportAccessStatus编码：" + code);
    }
}
