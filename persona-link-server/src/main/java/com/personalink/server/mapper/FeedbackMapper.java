package com.personalink.server.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalink.server.entity.FeedbackEntity;
import org.apache.ibatis.annotations.Mapper;
/** 反馈数据访问。 */
@Mapper
public interface FeedbackMapper extends BaseMapper<FeedbackEntity> {
    int insertIdempotent(FeedbackEntity entity);
}
