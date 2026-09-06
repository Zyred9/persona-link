package com.personalink.server.ai;

/** 提交时持久化的路由快照，不包含密钥；后续查询不得跟随默认供应商切换。 */
public record ImageProviderRoute(int provider, String modelName, String baseUrl, String region) { }
