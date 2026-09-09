package com.personalink.server.service;
import com.baomidou.mybatisplus.spring.service.IService;
import com.personalink.server.entity.ReportAccessEntity;
import com.personalink.server.dto.*;
public interface ReportAccessService extends IService<ReportAccessEntity> {
    ReportAccessResponse access(String openId, int reportType, Long reportId);
    ReportAccessResponse submitAd(String openId, int reportType, Long reportId, AdResultRequest request);
    boolean canRead(String openId, int reportType, Long reportId);
    void requireRead(String openId, int reportType, Long reportId);
    void grantFreeHistory(String openId);
}
