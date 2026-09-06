package com.personalink.server.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.personalink.server.entity.ImageGenerationTaskEntity;
import com.personalink.server.dto.ImageGenerationStartRequest;
import com.personalink.server.dto.ImageGenerationTaskResponse;

/** AI 双图任务业务。 */
public interface AiImageTaskService extends IService<ImageGenerationTaskEntity> {
    ImageGenerationTaskResponse create(Long testId, ImageGenerationStartRequest request, Long operatorId);
    ImageGenerationTaskResponse latest(Long testId);
    ImageGenerationTaskResponse get(Long taskId);
    ImageGenerationTaskResponse retry(Long taskId);
    ImageGenerationTaskResponse apply(Long taskId, Long operatorId);
}

