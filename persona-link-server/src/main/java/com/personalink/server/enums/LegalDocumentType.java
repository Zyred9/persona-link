package com.personalink.server.enums;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.personalink.server.exception.BusinessException;
/** 协议类型。 */
@Getter @RequiredArgsConstructor
public enum LegalDocumentType {
    USER(1, "用户协议"), PRIVACY(2, "隐私保护指引"), DISCLAIMER(3, "免责声明");
    private final int code;
    private final String desc;
    public static LegalDocumentType of(int code) {
        for (LegalDocumentType value : values()) {
            if (value.code == code) { return value; }
        }
        throw new BusinessException(400, "协议类型错误");
    }
}
