package com.personalink.server.ai;

/** 上游任务结果：1 等待，2 成功，3 失败。 */
public record ImageProviderResult(int state, String imageUrl, String errorMessage) { }
