package com.personalink.server.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.personalink.server.dto.MiniappAvatarResponse;
import com.personalink.server.dto.MiniappProfileRequest;
import com.personalink.server.dto.MiniappProfileResponse;
import com.personalink.server.entity.MiniappUserEntity;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.mapper.MiniappUserMapper;
import com.personalink.server.service.MiniappUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

/** 用户资料按 OpenID 持久化，历史会话首次访问时自动补建资料。 */
@Service
@RequiredArgsConstructor
public class MiniappUserServiceImpl extends ServiceImpl<MiniappUserMapper, MiniappUserEntity>
        implements MiniappUserService {
    private final LocalAssetService localAssetService;

    @Override
    public MiniappUserEntity findOrCreate(String openId) {
        MiniappUserEntity user = this.findByOpenId(openId);
        if (Objects.nonNull(user)) {
            return user;
        }
        user = new MiniappUserEntity();
        user.setOpenId(openId);
        user.setNickname("");
        user.setAvatarUrl("");
        user.setDeleted(0);
        try {
            if (!this.save(user)) {
                throw this.saveFailed();
            }
        } catch (DuplicateKeyException exception) {
            // 同一微信账号并发登录时，由唯一索引决定唯一用户。
            MiniappUserEntity existing = this.findByOpenId(openId);
            if (Objects.isNull(existing)) {
                throw exception;
            }
            return existing;
        }
        return user;
    }

    @Override
    public MiniappProfileResponse profile(String openId) {
        MiniappUserEntity user = this.findOrCreate(openId);
        return new MiniappProfileResponse(user.getNickname(), user.getAvatarUrl());
    }

    @Override
    public MiniappProfileResponse updateProfile(String openId, MiniappProfileRequest request) {
        MiniappUserEntity user = this.findOrCreate(openId);
        String avatarUrl = request.avatarUrl().trim();
        if (!avatarUrl.isEmpty() && !avatarUrl.equals(user.getAvatarUrl())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40004, "请先上传当前账号的头像");
        }
        String nickname = request.nickname().trim();
        // 仅更新资料字段，避免覆盖并发上传之外的用户数据。
        if (!this.update(Wrappers.<MiniappUserEntity>lambdaUpdate()
                .set(MiniappUserEntity::getNickname, nickname)
                .set(MiniappUserEntity::getAvatarUrl, avatarUrl)
                .eq(MiniappUserEntity::getId, user.getId())
                .eq(MiniappUserEntity::getAvatarUrl, user.getAvatarUrl())
                .eq(MiniappUserEntity::getDeleted, 0))) {
            throw new BusinessException(HttpStatus.CONFLICT, 40904, "资料已变化，请刷新后重试");
        }
        return new MiniappProfileResponse(nickname, avatarUrl);
    }

    @Override
    public MiniappAvatarResponse uploadAvatar(String openId, MultipartFile file) {
        MiniappUserEntity user = this.findOrCreate(openId);
        String avatarUrl = this.localAssetService.saveAvatarImage(file).url();
        if (!this.update(Wrappers.<MiniappUserEntity>lambdaUpdate()
                .set(MiniappUserEntity::getAvatarUrl, avatarUrl)
                .eq(MiniappUserEntity::getId, user.getId())
                .eq(MiniappUserEntity::getDeleted, 0))) {
            throw this.saveFailed();
        }
        return new MiniappAvatarResponse(avatarUrl);
    }

    private MiniappUserEntity findByOpenId(String openId) {
        return this.getOne(Wrappers.<MiniappUserEntity>lambdaQuery()
                .eq(MiniappUserEntity::getOpenId, openId)
                .eq(MiniappUserEntity::getDeleted, 0), false);
    }

    private BusinessException saveFailed() {
        return new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 50003, "保存用户资料失败，请重试");
    }
}
