package com.personalink.server.dto;

import java.time.LocalDateTime;

/** 素材历史项。 */
public record AssetItemResponse(
        String fileName,
        String url,
        String imageType,
        long size,
        LocalDateTime updatedAt,
        long referenceCount) {
}
