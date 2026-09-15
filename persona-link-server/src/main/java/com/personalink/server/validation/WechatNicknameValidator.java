package com.personalink.server.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** 微信昵称长度校验：ASCII 字符算 1 个长度单位，其余字符算 2 个，总长不超过 32；null 交给 @NotNull 处理。 */
public class WechatNicknameValidator implements ConstraintValidator<WechatNickname, String> {

    private static final int MAX_UNITS = 32;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        int units = 0;
        for (int index = 0; index < value.length(); ) {
            int codePoint = value.codePointAt(index);
            units += codePoint <= 0x7f ? 1 : 2;
            if (units > MAX_UNITS) {
                return false;
            }
            index += Character.charCount(codePoint);
        }
        return true;
    }
}
