package com.personalink.server.service;
import com.baomidou.mybatisplus.spring.service.IService;
import com.personalink.server.dto.*;
import com.personalink.server.entity.LegalDocumentEntity;
import java.util.List;
/** 协议发布查询服务。 */
public interface LegalDocumentService extends IService<LegalDocumentEntity> {
    List<LegalDocumentVersionResponse> versions();
    List<LegalDocumentResponse> documents();
    LegalDocumentResponse published(int type);
    LegalDocumentResponse publish(int type, LegalDocumentSaveRequest request);
}
