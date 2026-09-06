package com.personalink.server.service;

import com.personalink.server.dto.AiGenerationStartRequest;
import com.personalink.server.dto.AiGenerationTaskResponse;
import com.personalink.server.dto.AiGenerationTaskQuery;
import com.personalink.server.dto.AiGenerationTaskSummaryResponse;
import com.personalink.server.dto.PageResponse;

/** AI 题库生成任务业务。 */
public interface AiQuestionBankService {

    AiGenerationTaskResponse create(AiGenerationStartRequest request, Long operatorId);

    PageResponse<AiGenerationTaskSummaryResponse> page(AiGenerationTaskQuery query);

    AiGenerationTaskResponse get(Long taskId);

    AiGenerationTaskResponse retry(Long taskId);

    AiGenerationTaskResponse submit(Long taskId);
}
