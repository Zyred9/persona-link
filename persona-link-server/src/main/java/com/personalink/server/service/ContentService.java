package com.personalink.server.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.personalink.server.dto.PageResponse;
import com.personalink.server.dto.AuditLogQuery;
import com.personalink.server.dto.AuditLogResponse;
import com.personalink.server.dto.PublishCheckResponse;
import com.personalink.server.dto.QuestionResponse;
import com.personalink.server.dto.QuestionSaveRequest;
import com.personalink.server.dto.ResultConfigResponse;
import com.personalink.server.dto.ResultConfigSaveRequest;
import com.personalink.server.dto.TestQuery;
import com.personalink.server.dto.TestResponse;
import com.personalink.server.dto.TestSaveRequest;
import com.personalink.server.dto.TestVersionResponse;
import com.personalink.server.dto.TestVersionSaveRequest;
import com.personalink.server.entity.TestEntity;

import java.util.List;
import java.time.LocalDateTime;

/** 人工内容运营业务。 */
public interface ContentService extends IService<TestEntity> {
    PageResponse<TestResponse> pageTests(TestQuery query);
    TestResponse getTest(Long id);
    TestResponse createTest(TestSaveRequest request, Long operatorId);
    TestResponse updateTest(Long id, TestSaveRequest request, Long operatorId);
    TestResponse updateTestStatus(Long id, Integer status, Long operatorId);
    TestResponse updateTestHomeDisplay(Long id, Integer homeDisplay, Integer homeSort, Long operatorId);
    void deleteTests(List<Long> ids, Long operatorId);
    List<TestVersionResponse> listVersions(Long testId);
    TestVersionResponse getVersion(Long versionId);
    TestVersionResponse createVersion(Long testId, TestVersionSaveRequest request, Long operatorId);
    TestVersionResponse copyVersionAsDraft(Long versionId, Long operatorId);
    /** 仅把生成图片写入草稿，返回草稿 ID，不发布、不覆盖其他编辑字段。 */
    Long applyGeneratedImages(Long testId, String coverUrl, String detailImageUrl, Long operatorId);
    TestVersionResponse updateVersion(Long versionId, TestVersionSaveRequest request, Long operatorId);
    List<QuestionResponse> listQuestions(Long versionId);
    QuestionResponse createQuestion(Long versionId, QuestionSaveRequest request, Long operatorId);
    int appendGeneratedQuestions(Long versionId, List<QuestionSaveRequest> requests, Long operatorId);
    QuestionResponse updateQuestion(Long questionId, QuestionSaveRequest request, Long operatorId);
    void deleteQuestion(Long questionId, Long operatorId);
    ResultConfigResponse getResultConfig(Long versionId);
    ResultConfigResponse saveResultConfig(Long versionId, ResultConfigSaveRequest request, Long operatorId);
    PublishCheckResponse checkPublish(Long versionId);
    TestVersionResponse schedule(Long versionId, LocalDateTime scheduledAt, Long operatorId);
    TestVersionResponse cancelSchedule(Long versionId, Long operatorId);
    TestVersionResponse publish(Long versionId, Long operatorId);
    TestVersionResponse offline(Long versionId, String reason, Long operatorId);
    TestVersionResponse archive(Long versionId, Long operatorId);
    int publishDueVersions();
    PageResponse<AuditLogResponse> pageAudits(AuditLogQuery query);
}
