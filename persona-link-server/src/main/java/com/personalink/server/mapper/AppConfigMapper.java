package com.personalink.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalink.server.entity.AppConfigEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AppConfigMapper extends BaseMapper<AppConfigEntity> {
    /** 唯一键原子写入，避免首次保存并发产生重复配置。 */
    void upsert(AppConfigEntity config);
    /** 恢复软删除配置，条件更新避免覆盖并发恢复后的值。 */
    int restoreDeleted(AppConfigEntity config);
}
