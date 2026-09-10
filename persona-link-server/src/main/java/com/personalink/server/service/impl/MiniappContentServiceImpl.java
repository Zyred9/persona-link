package com.personalink.server.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.dto.MiniappHomeCategoryResponse;
import com.personalink.server.dto.MiniappHomeResponse;
import com.personalink.server.dto.MiniappHomeTestResponse;
import com.personalink.server.dto.MiniappTestDetailResponse;
import com.personalink.server.entity.TestEntity;
import com.personalink.server.mapper.MiniappContentMapper;
import com.personalink.server.service.MiniappContentService;
import com.personalink.server.service.AppConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 小程序已发布内容服务实现。
 */
@Service
@RequiredArgsConstructor
public class MiniappContentServiceImpl extends ServiceImpl<MiniappContentMapper, TestEntity>
        implements MiniappContentService {

    private static final int REGULAR = 0;
    private static final int FOCUS_SLOT = 1;
    private static final int RECOMMENDED_SLOT = 2;
    private final AppConfigService appConfigService;

    @Override
    @Transactional(readOnly = true)
    public MiniappHomeResponse getHome() {
        List<MiniappHomeCategoryResponse> categories = this.baseMapper.selectHomeCategories();
        return new MiniappHomeResponse(
                categories,
                this.baseMapper.selectHomeTests(FOCUS_SLOT),
                this.baseMapper.selectHomeTests(RECOMMENDED_SLOT),
                this.baseMapper.selectHomeTests(REGULAR),
                this.appConfigService.readHomeConfig().titleImageUrl());
    }

    @Override
    @Transactional(readOnly = true)
    public MiniappTestDetailResponse getPublishedTestDetail(Long testId) {
        MiniappTestDetailResponse response = this.baseMapper.selectPublishedTestDetail(testId);
        if (Objects.isNull(response)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40401, "题型不存在或暂不可用");
        }
        return response;
    }
}
