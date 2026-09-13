package com.personalink.server.dto;

/**
 * 双人测试页面图片配置，空字符串表示不展示。
 * @param joinHeroImageUrl 加入双人测试页头图地址
 * @param waitingHeroImageUrl 匹配进度页一方完成时的头图地址
 * @param completedHeroImageUrl 匹配进度页双方完成时的头图地址
 */
public record PairConfigResponse(String joinHeroImageUrl, String waitingHeroImageUrl, String completedHeroImageUrl) {
}
