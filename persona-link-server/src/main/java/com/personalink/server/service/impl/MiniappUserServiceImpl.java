package com.personalink.server.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.personalink.server.dto.MiniappAvatarResponse;
import com.personalink.server.dto.MiniappProfileRequest;
import com.personalink.server.dto.MiniappProfileResponse;
import com.personalink.server.entity.MiniappUserEntity;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.mapper.MiniappUserMapper;
import com.personalink.server.miniapp.security.WechatContentSecurityClient;
import com.personalink.server.service.AppConfigService;
import com.personalink.server.service.MiniappUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/** 用户资料按 OpenID 持久化，历史会话首次访问时自动补建资料。 */
@Service
@RequiredArgsConstructor
public class MiniappUserServiceImpl extends ServiceImpl<MiniappUserMapper, MiniappUserEntity>
        implements MiniappUserService {
    /** 默认头像配置键。 */
    private static final String DEFAULT_AVATAR_CONFIG_KEY = "miniapp.default_avatar_url";
    /** 生成的默认昵称格式：用户 + 6 位随机数字，不查重。 */
    private static final Pattern GENERATED_NICKNAME_PATTERN = Pattern.compile("^用户\\d{6}$");

    private final LocalAssetService localAssetService;
    private final AppConfigService appConfigService;
    private final WechatContentSecurityClient contentSecurityClient;

    @Override
    public MiniappUserEntity findOrCreate(String openId) {
        MiniappUserEntity user = this.findByOpenId(openId);
        if (Objects.nonNull(user)) {
            return this.applyDefaultProfile(user);
        }
        user = new MiniappUserEntity();
        user.setOpenId(openId);
        user.setNickname(this.generateDefaultNickname());
        user.setAvatarUrl(this.defaultAvatarUrl());
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
            return this.applyDefaultProfile(existing);
        }
        return user;
    }

    @Override
    public MiniappProfileResponse profile(String openId) {
        MiniappUserEntity user = this.findOrCreate(openId);
        return this.toResponse(user);
    }

    @Override
    public MiniappProfileResponse updateProfile(String openId, MiniappProfileRequest request) {
        MiniappUserEntity user = this.findOrCreate(openId);
        String avatarUrl = request.avatarUrl().trim();
        if (!avatarUrl.isEmpty() && !avatarUrl.equals(user.getAvatarUrl())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40004, "请先上传当前账号的头像");
        }
        String nickname = request.nickname().trim();
        if (nickname.isEmpty()) {
            // 清空昵称视为使用默认昵称，避免资料表出现空昵称。
            nickname = this.generateDefaultNickname();
        } else if (!Objects.equals(nickname, user.getNickname())
                && !this.contentSecurityClient.isTextAllowed(openId, nickname,
                WechatContentSecurityClient.SCENE_PROFILE)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40005, "昵称含违规信息，请修改后重新提交");
        }
        // 仅更新资料字段，避免覆盖并发上传之外的用户数据。
        if (!this.update(Wrappers.<MiniappUserEntity>lambdaUpdate()
                .set(MiniappUserEntity::getNickname, nickname)
                .set(MiniappUserEntity::getAvatarUrl, avatarUrl)
                .eq(MiniappUserEntity::getId, user.getId())
                .eq(MiniappUserEntity::getAvatarUrl, user.getAvatarUrl())
                .eq(MiniappUserEntity::getDeleted, 0))) {
            throw new BusinessException(HttpStatus.CONFLICT, 40904, "资料已变化，请刷新后重试");
        }
        return new MiniappProfileResponse(nickname, avatarUrl, this.isCustomized(nickname, avatarUrl));
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void anonymize(String openId) {
        MiniappUserEntity user = this.findByOpenId(openId);
        if (Objects.isNull(user)) {
            return;
        }
        // 注销后抹除 OpenID 与资料，同一微信再次登录视为全新账号。
        String anonymizedOpenId = "cancelled:" + user.getId() + ":" + UUID.randomUUID().toString().replace("-", "");
        this.lambdaUpdate()
                .set(MiniappUserEntity::getOpenId, anonymizedOpenId)
                .set(MiniappUserEntity::getNickname, "")
                .set(MiniappUserEntity::getAvatarUrl, "")
                .set(MiniappUserEntity::getDeleted, 1)
                .eq(MiniappUserEntity::getId, user.getId())
                .eq(MiniappUserEntity::getDeleted, 0)
                .update();
    }

    private MiniappUserEntity findByOpenId(String openId) {
        return this.getOne(Wrappers.<MiniappUserEntity>lambdaQuery()
                .eq(MiniappUserEntity::getOpenId, openId)
                .eq(MiniappUserEntity::getDeleted, 0), false);
    }

    /** 用户未提供头像昵称时写入默认值；已有资料保持不变。 */
    private MiniappUserEntity applyDefaultProfile(MiniappUserEntity user) {
        String nextNickname = StringUtils.hasText(user.getNickname()) ? user.getNickname() : this.generateDefaultNickname();
        String nextAvatarUrl = StringUtils.hasText(user.getAvatarUrl()) ? user.getAvatarUrl() : this.defaultAvatarUrl();
        if (Objects.equals(nextNickname, user.getNickname()) && Objects.equals(nextAvatarUrl, user.getAvatarUrl())) {
            return user;
        }
        this.update(Wrappers.<MiniappUserEntity>lambdaUpdate()
                .set(MiniappUserEntity::getNickname, nextNickname)
                .set(MiniappUserEntity::getAvatarUrl, nextAvatarUrl)
                .eq(MiniappUserEntity::getId, user.getId())
                .eq(MiniappUserEntity::getDeleted, 0));
        user.setNickname(nextNickname);
        user.setAvatarUrl(nextAvatarUrl);
        return user;
    }

    /** 生成「用户 + 6 位随机数字」的默认昵称，按要求不查重。 */
    private String generateDefaultNickname() {
        return "用户" + ThreadLocalRandom.current().nextInt(100000, 1000000);
    }

    private MiniappProfileResponse toResponse(MiniappUserEntity user) {
        return new MiniappProfileResponse(user.getNickname(), user.getAvatarUrl(),
                this.isCustomized(user.getNickname(), user.getAvatarUrl()));
    }

    /** 头像为本人上传、昵称非生成值时，视为用户提供了资料。 */
    private boolean isCustomized(String nickname, String avatarUrl) {
        if (!StringUtils.hasText(nickname) || !StringUtils.hasText(avatarUrl)) {
            return false;
        }
        return !GENERATED_NICKNAME_PATTERN.matcher(nickname).matches()
                && !Objects.equals(avatarUrl, this.defaultAvatarUrl());
    }

    private String defaultAvatarUrl() {
        return this.readDefaultValue(DEFAULT_AVATAR_CONFIG_KEY, "");
    }

    private String readDefaultValue(String configKey, String fallback) {
        Map<String, String> values = this.appConfigService.readPublicValues(List.of(configKey));
        String value = Objects.isNull(values) ? null : values.get(configKey);
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private BusinessException saveFailed() {
        return new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 50003, "保存用户资料失败，请重试");
    }
}
