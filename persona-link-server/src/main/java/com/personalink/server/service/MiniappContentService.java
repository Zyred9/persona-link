package com.personalink.server.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.personalink.server.dto.MiniappHomeResponse;
import com.personalink.server.dto.MiniappTestDetailResponse;
import com.personalink.server.entity.TestEntity;

/**
 * 小程序已发布内容服务。
 */
public interface MiniappContentService extends IService<TestEntity> {

    MiniappHomeResponse getHome();

    MiniappTestDetailResponse getPublishedTestDetail(Long testId);
}
