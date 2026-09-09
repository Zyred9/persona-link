package com.personalink.server.enums;
/** 广告报告领域数字枚举。 */
public enum AdFailurePolicy {
    FREE(1, "免费放行"),
    RETRY(2, "稍后重试");
    private final int code;
    private final String desc;
    AdFailurePolicy(int code, String desc) { this.code = code; this.desc = desc; }
    public int getCode() { return this.code; }
    public String getDesc() { return this.desc; }
    public static AdFailurePolicy of(int code) {
        for (AdFailurePolicy value : values()) { if (value.code == code) { return value; } }
        throw new IllegalArgumentException("无效AdFailurePolicy编码：" + code);
    }
}
