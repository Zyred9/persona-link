package com.personalink.server.dto;

/**
 * 头像上传结果。
 *
 * @param avatarUrl 当前生效的头像地址，审核期间保持旧值或空串
 * @param reviewing 新头像是否已提交内容安全审核，通过后自动替换生效头像
 */
public record MiniappAvatarResponse(String avatarUrl, boolean reviewing) {
}
