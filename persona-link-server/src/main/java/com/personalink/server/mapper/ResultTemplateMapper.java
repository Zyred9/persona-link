package com.personalink.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalink.server.entity.ResultTemplateEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 结果模板数据访问接口。 */
@Mapper
public interface ResultTemplateMapper extends BaseMapper<ResultTemplateEntity> {
    int insertBatch(@Param("items") List<ResultTemplateEntity> items);
}
