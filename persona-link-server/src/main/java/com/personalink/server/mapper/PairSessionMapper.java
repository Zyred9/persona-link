package com.personalink.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalink.server.entity.PairSessionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.personalink.server.dto.PairHistoryResponse;
import java.time.LocalDateTime;
import java.util.List;

/** 双人配对数据访问接口。 */
@Mapper
public interface PairSessionMapper extends BaseMapper<PairSessionEntity> {

    int insertIgnore(PairSessionEntity entity);

    long countHistory(@Param("openId") String openId);

    List<PairHistoryResponse> selectHistory(@Param("openId") String openId,
            @Param("offset") long offset, @Param("size") long size,
            @Param("now") LocalDateTime now, @Param("pendingStatus") int pendingStatus,
            @Param("expiredStatus") int expiredStatus);
}
