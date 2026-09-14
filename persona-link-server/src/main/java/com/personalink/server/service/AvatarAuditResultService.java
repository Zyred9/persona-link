package com.personalink.server.service;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.personalink.server.entity.AvatarAuditResultEntity;
import com.personalink.server.mapper.AvatarAuditResultMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import java.util.Objects;

/** 审核回调先持久化再应用，重复推送不覆盖首次结论。 */
@Service
public class AvatarAuditResultService extends ServiceImpl<AvatarAuditResultMapper, AvatarAuditResultEntity> {
    public boolean record(String traceId, boolean passed) {
        Assert.hasText(traceId, "头像审核任务号不能为空");
        AvatarAuditResultEntity result = new AvatarAuditResultEntity();
        result.setTraceId(traceId);
        result.setPassed(passed);
        result.setDeleted(0);
        try {
            Assert.state(this.save(result), "保存头像审核结果失败");
        } catch (DuplicateKeyException duplicate) {
            // 微信重复推送同一任务时，始终采用已持久化的首次结论。
        }
        Boolean stored = this.findResult(traceId);
        Assert.state(Objects.nonNull(stored), "读取头像审核结果失败");
        return stored;
    }

    public Boolean findResult(String traceId) {
        AvatarAuditResultEntity result = this.lambdaQuery()
                .eq(AvatarAuditResultEntity::getTraceId, traceId)
                .eq(AvatarAuditResultEntity::getDeleted, 0)
                .one();
        return Objects.isNull(result) ? null : result.getPassed();
    }
}
