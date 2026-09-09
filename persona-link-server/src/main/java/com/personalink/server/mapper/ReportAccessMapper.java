package com.personalink.server.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalink.server.entity.ReportAccessEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
/** 报告授权数据访问。 */
@Mapper
public interface ReportAccessMapper extends BaseMapper<ReportAccessEntity> {
    int insertIgnore(ReportAccessEntity access);
    int grantHistory(@Param("openId") String openId);
}
