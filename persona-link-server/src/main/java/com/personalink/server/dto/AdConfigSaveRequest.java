package com.personalink.server.dto;
import jakarta.validation.constraints.*;
/** 全局广告配置；只接受激励视频广告位。 */
public record AdConfigSaveRequest(@NotNull Boolean enabled, @NotNull @Size(max=71) String adUnitId,
        @NotNull @Min(1) @Max(2) Integer failurePolicy) {
    public AdConfigSaveRequest { adUnitId = adUnitId == null ? null : adUnitId.trim(); }
    @AssertTrue(message="开启广告须填写有效的 adunit- 广告位 ID")
    public boolean isAdUnitValid() {
        return adUnitId != null && (adUnitId.isEmpty() ? !Boolean.TRUE.equals(enabled)
                : adUnitId.matches("adunit-[A-Za-z0-9]{1,64}"));
    }
}
