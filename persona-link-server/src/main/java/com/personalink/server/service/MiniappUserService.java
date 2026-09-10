package com.personalink.server.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.personalink.server.dto.MiniappAvatarResponse;
import com.personalink.server.dto.MiniappProfileRequest;
import com.personalink.server.dto.MiniappProfileResponse;
import com.personalink.server.entity.MiniappUserEntity;
import org.springframework.web.multipart.MultipartFile;

/** 基于已认证 OpenID 的小程序用户资料服务。 */
public interface MiniappUserService extends IService<MiniappUserEntity> {
    MiniappUserEntity findOrCreate(String openId);
    MiniappProfileResponse profile(String openId);
    MiniappProfileResponse updateProfile(String openId, MiniappProfileRequest request);
    MiniappAvatarResponse uploadAvatar(String openId, MultipartFile file);
}
