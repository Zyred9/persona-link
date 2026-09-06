package com.personalink.server.dto;

/**
 * 当前后台账号信息。
 *
 * @param adminId 后台账号 Long 主键
 * @param displayName 显示名称
 * @param roleType 角色类型：1管理员，2内容运营，3只读查看
 */
public record AdminProfileResponse(Long adminId, String displayName, Integer roleType) {
}
