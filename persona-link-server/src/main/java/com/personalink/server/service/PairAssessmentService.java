package com.personalink.server.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.personalink.server.dto.CreatePairRequest;
import com.personalink.server.dto.JoinPairRequest;
import com.personalink.server.dto.PairCreateResponse;
import com.personalink.server.dto.PairReportResponse;
import com.personalink.server.dto.PairSessionResponse;
import com.personalink.server.entity.PairSessionEntity;

/**
 * 小程序双人配对业务接口。
 */
public interface PairAssessmentService extends IService<PairSessionEntity> {

    PairCreateResponse create(String openId, CreatePairRequest request);

    PairSessionResponse join(String openId, JoinPairRequest request);

    PairSessionResponse getStatus(String openId, Long pairSessionId);

    void cancel(String openId, Long pairSessionId);

    PairReportResponse getReport(String openId, Long pairSessionId);
}
