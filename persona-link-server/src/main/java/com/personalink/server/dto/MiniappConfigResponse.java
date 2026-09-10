package com.personalink.server.dto;

/** 小程序公开配置白名单，禁止直接返回通用配置表。 */
public record MiniappConfigResponse(String version) {}
