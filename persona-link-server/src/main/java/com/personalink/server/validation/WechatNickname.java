package com.personalink.server.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 微信昵称长度约束：最多 16 个汉字或 32 个字符。 */
@Documented
@Constraint(validatedBy = WechatNicknameValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE,
        ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface WechatNickname {

    String message() default "昵称不能超过16个汉字或32个字符";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
