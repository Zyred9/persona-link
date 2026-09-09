package com.personalink.server.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalink.server.entity.AnswerSessionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
/** 隐私删除的跨表批量操作，不物理删除业务记录。 */
@Mapper
public interface TestRecordMapper extends BaseMapper<AnswerSessionEntity> {
    int hidePairs(@Param("openId") String openId);
    int deleteAnswers(@Param("openId") String openId);
    int deleteSnapshots(@Param("openId") String openId);
    int deleteReports(@Param("openId") String openId);
    int deleteGrants(@Param("openId") String openId);
    int deleteAdTasks(@Param("openId") String openId);
}
