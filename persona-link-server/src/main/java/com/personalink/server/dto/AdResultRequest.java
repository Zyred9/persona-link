package com.personalink.server.dto;
import jakarta.validation.constraints.*;
/** 客户端广告事件，仅为业务信号，不能证明真实观看。 */
public record AdResultRequest(
        @NotBlank @Pattern(regexp="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String taskId,
        @NotNull @Min(1) @Max(2) Integer outcome,
        @Min(1000) @Max(1009) Integer errorCode) {
    @AssertTrue(message="广告加载失败必须提供微信广告错误码")
    public boolean isErrorValid() { return !Integer.valueOf(2).equals(outcome) || errorCode != null; }
}
