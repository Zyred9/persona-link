package com.personalink.server.service;
import com.baomidou.mybatisplus.spring.service.IService;
import com.personalink.server.entity.AnswerSessionEntity;
/** 本人测试记录删除。 */
public interface TestRecordService extends IService<AnswerSessionEntity> {
    void deleteAll(String openId);
}
