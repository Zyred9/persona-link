package com.personalink.server.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalink.server.entity.LegalDocumentEntity;
import org.apache.ibatis.annotations.Mapper;
/** 协议数据访问。 */
@Mapper
public interface LegalDocumentMapper extends BaseMapper<LegalDocumentEntity> {
    int publish(LegalDocumentEntity entity);
}
