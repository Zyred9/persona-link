package com.personalink.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalink.server.entity.PairReportEntity;
import org.apache.ibatis.annotations.Mapper;

/** 双人报告数据访问接口。 */
@Mapper
public interface PairReportMapper extends BaseMapper<PairReportEntity> {

    int insertIgnore(PairReportEntity entity);
}
