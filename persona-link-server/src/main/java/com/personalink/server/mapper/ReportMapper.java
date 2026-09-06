package com.personalink.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalink.server.entity.ReportEntity;
import com.personalink.server.dto.ReportHistoryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 单人报告数据访问接口。 */
@Mapper
public interface ReportMapper extends BaseMapper<ReportEntity> {

    int insertIgnore(ReportEntity entity);

    long countHistory(@Param("openId") String openId);

    List<ReportHistoryRow> selectHistory(@Param("openId") String openId,
                                         @Param("offset") long offset,
                                         @Param("size") long size);
}
