package com.personalink.server.dto;

import com.personalink.server.entity.ImageGenerationTaskEntity;

/** 生图进度，不向客户端暴露服务端配置和供应商任务标识。 */
public record ImageGenerationTaskResponse(Long id, Long testId, Integer provider, String modelName,
        String promptText, Integer taskStatus, String coverUrl, String detailImageUrl,
        String errorMessage, Long appliedVersionId,
        Integer coverWidth, Integer coverHeight, Integer detailWidth, Integer detailHeight) {
    public static ImageGenerationTaskResponse build(ImageGenerationTaskEntity task) {
        return new ImageGenerationTaskResponse(task.getId(), task.getTestId(), task.getProvider(),
                task.getModelName(), task.getPromptText(), task.getTaskStatus(), task.getCoverUrl(),
                task.getDetailImageUrl(), task.getErrorMessage(), task.getAppliedVersionId(),
                task.getCoverWidth(), task.getCoverHeight(), task.getDetailWidth(), task.getDetailHeight());
    }
}
