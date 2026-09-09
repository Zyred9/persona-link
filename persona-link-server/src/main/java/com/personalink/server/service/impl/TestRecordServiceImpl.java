package com.personalink.server.service.impl;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.personalink.server.entity.AnswerSessionEntity;
import com.personalink.server.mapper.TestRecordMapper;
import com.personalink.server.service.TestRecordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/** 隐私清理与答卷提交共用答卷行锁；完成的双人快照为对方保留。 */
@Service
public class TestRecordServiceImpl extends ServiceImpl<TestRecordMapper, AnswerSessionEntity> implements TestRecordService {
    @Override
    @Transactional(rollbackFor=Exception.class)
    public void deleteAll(String openId) {
        // 锁顺序与提交一致：答卷 -> 配对 -> 报告授权；先锁再删除，避免迟到提交重建报告。
        this.lambdaQuery().eq(AnswerSessionEntity::getOpenId, openId)
                .eq(AnswerSessionEntity::getDeleted, 0).orderByAsc(AnswerSessionEntity::getId)
                .last("FOR UPDATE").list();
        this.baseMapper.hidePairs(openId);
        this.baseMapper.deleteAnswers(openId);
        this.baseMapper.deleteSnapshots(openId);
        this.baseMapper.deleteReports(openId);
        this.baseMapper.deleteGrants(openId);
        this.baseMapper.deleteAdTasks(openId);
        this.lambdaUpdate().set(AnswerSessionEntity::getDeleted, 1)
                .eq(AnswerSessionEntity::getOpenId, openId).eq(AnswerSessionEntity::getDeleted, 0).update();
    }
}
