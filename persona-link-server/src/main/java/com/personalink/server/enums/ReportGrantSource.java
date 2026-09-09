package com.personalink.server.enums;
/** 广告报告领域数字枚举。 */
public enum ReportGrantSource {
    AD_DISABLED(1, "广告关闭"),
    AD_COMPLETED(2, "完整观看"),
    AD_FAILURE(3, "失败放行"),
    MIGRATION(4, "历史迁移");
    private final int code;
    private final String desc;
    ReportGrantSource(int code, String desc) { this.code = code; this.desc = desc; }
    public int getCode() { return this.code; }
    public String getDesc() { return this.desc; }
    public static ReportGrantSource of(int code) {
        for (ReportGrantSource value : values()) { if (value.code == code) { return value; } }
        throw new IllegalArgumentException("无效ReportGrantSource编码：" + code);
    }
}
