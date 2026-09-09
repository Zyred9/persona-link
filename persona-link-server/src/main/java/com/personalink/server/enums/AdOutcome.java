package com.personalink.server.enums;
/** 广告报告领域数字枚举。 */
public enum AdOutcome {
    COMPLETED(1, "完整观看"),
    FAILED(2, "加载失败");
    private final int code;
    private final String desc;
    AdOutcome(int code, String desc) { this.code = code; this.desc = desc; }
    public int getCode() { return this.code; }
    public String getDesc() { return this.desc; }
    public static AdOutcome of(int code) {
        for (AdOutcome value : values()) { if (value.code == code) { return value; } }
        throw new IllegalArgumentException("无效AdOutcome编码：" + code);
    }
}
