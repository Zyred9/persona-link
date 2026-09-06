package com.personalink.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalink.server.dto.TestQuery;
import com.personalink.server.dto.TestResponse;
import com.personalink.server.entity.TestEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 题型数据访问接口。 */
@Mapper
public interface TestMapper extends BaseMapper<TestEntity> {
    List<TestResponse> selectTestPage(@Param("query") TestQuery query,
                                      @Param("offset") long offset,
                                      @Param("size") long size);
    long countTestPage(@Param("query") TestQuery query);
    TestResponse selectTestDetail(@Param("id") Long id);
}
