package com.personalink.server.service.impl;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.personalink.server.dto.*;
import com.personalink.server.entity.LegalDocumentEntity;
import com.personalink.server.enums.LegalDocumentType;
import com.personalink.server.mapper.LegalDocumentMapper;
import com.personalink.server.service.LegalDocumentService;
import com.personalink.server.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
/** 管理员保存后即对客户端发布；不以占位文本冒充正式协议。 */
@Service
public class LegalDocumentServiceImpl extends ServiceImpl<LegalDocumentMapper, LegalDocumentEntity> implements LegalDocumentService {
    @Override
    public List<LegalDocumentVersionResponse> versions() {
        return this.lambdaQuery()
                .select(LegalDocumentEntity::getType, LegalDocumentEntity::getVersion)
                .eq(LegalDocumentEntity::getDeleted, 0)
                .orderByAsc(LegalDocumentEntity::getType)
                .list().stream()
                .map(entity -> new LegalDocumentVersionResponse(entity.getType(), entity.getVersion()))
                .toList();
    }

    @Override
    public List<LegalDocumentResponse> documents() {
        Map<Integer, LegalDocumentEntity> configured = this.lambdaQuery().eq(LegalDocumentEntity::getDeleted, 0).list()
                .stream().collect(Collectors.toMap(LegalDocumentEntity::getType, Function.identity(), (first, second) -> first));
        return Arrays.stream(LegalDocumentType.values()).map(type -> {
            LegalDocumentEntity entity = configured.get(type.getCode());
            return Objects.isNull(entity) ? new LegalDocumentResponse(type.getCode(), type.getDesc(), "", 0, null)
                    : this.response(entity);
        }).toList();
    }
    @Override
    public LegalDocumentResponse published(int type) {
        LegalDocumentType.of(type);
        LegalDocumentEntity entity = this.lambdaQuery().eq(LegalDocumentEntity::getType, type)
                .eq(LegalDocumentEntity::getDeleted, 0).one();
        if (Objects.isNull(entity)) { throw new BusinessException(HttpStatus.NOT_FOUND, 404, "协议尚未配置，请稍后重试"); }
        return this.response(entity);
    }
    @Override
    @Transactional(rollbackFor=Exception.class)
    public LegalDocumentResponse publish(int type, LegalDocumentSaveRequest request) {
        LegalDocumentType.of(type);
        LegalDocumentEntity entity = new LegalDocumentEntity();
        entity.setType(type);
        entity.setTitle(request.title());
        entity.setContent(request.content());
        this.baseMapper.publish(entity);
        return this.published(type);
    }
    private LegalDocumentResponse response(LegalDocumentEntity entity) {
        return new LegalDocumentResponse(entity.getType(), entity.getTitle(), entity.getContent(), entity.getVersion(), entity.getUpdateDate());
    }
}
