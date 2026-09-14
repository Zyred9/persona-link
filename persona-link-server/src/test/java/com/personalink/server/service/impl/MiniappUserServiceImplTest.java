package com.personalink.server.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.personalink.server.dto.AssetResponse;
import com.personalink.server.dto.MiniappProfileRequest;
import com.personalink.server.entity.MiniappUserEntity;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.miniapp.security.WechatContentSecurityClient;
import com.personalink.server.service.AppConfigService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MiniappUserServiceImplTest {
    private LocalAssetService assets;
    private AppConfigService configs;
    private WechatContentSecurityClient security;
    private MiniappUserServiceImpl service;

    @BeforeEach
    void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "profile-test"),
                MiniappUserEntity.class);
        this.assets = mock(LocalAssetService.class);
        this.configs = mock(AppConfigService.class);
        this.security = mock(WechatContentSecurityClient.class);
        when(this.security.isTextAllowed(any(), any(), anyInt())).thenReturn(true);
        this.service = spy(new MiniappUserServiceImpl(this.assets, this.configs, this.security));
    }

    @Test
    void existingAccountRetainsProfileAndConcurrentFirstLoginReusesUniqueUser() {
        MiniappUserEntity existing = this.user();
        doReturn(existing).when(this.service).getOne(any(Wrapper.class), eq(false));
        assertSame(existing, this.service.findOrCreate("openid-a"));
        verify(this.service, never()).save(any(MiniappUserEntity.class));

        doReturn(null, existing).when(this.service).getOne(any(Wrapper.class), eq(false));
        doThrow(new DuplicateKeyException("concurrent login")).when(this.service).save(any(MiniappUserEntity.class));
        assertSame(existing, this.service.findOrCreate("openid-a"));
    }

    @Test
    void newAccountUsesConfiguredAvatarAndGeneratedNickname() {
        when(this.configs.readPublicValues(anyList()))
                .thenReturn(Map.of("miniapp.default_avatar_url", "/assets/default-avatar.png"));
        doReturn(null).when(this.service).getOne(any(Wrapper.class), eq(false));
        doReturn(true).when(this.service).save(any(MiniappUserEntity.class));
        MiniappUserEntity created = this.service.findOrCreate("openid-a");
        assertEquals("openid-a", created.getOpenId());
        assertTrue(created.getNickname().matches("用户\\d{6}"), "默认昵称为用户加 6 位随机数字");
        assertEquals("/assets/default-avatar.png", created.getAvatarUrl());
    }

    @Test
    void newAccountGeneratesNicknameWhenConfigMissing() {
        when(this.configs.readPublicValues(anyList())).thenReturn(Map.of());
        doReturn(null).when(this.service).getOne(any(Wrapper.class), eq(false));
        doReturn(true).when(this.service).save(any(MiniappUserEntity.class));
        MiniappUserEntity created = this.service.findOrCreate("openid-a");
        assertTrue(created.getNickname().matches("用户\\d{6}"));
        assertEquals("", created.getAvatarUrl());
    }

    @Test
    void existingEmptyProfileBackfillsDefaults() {
        MiniappUserEntity existing = this.user();
        existing.setNickname("");
        existing.setAvatarUrl("");
        doReturn(existing).when(this.service).getOne(any(Wrapper.class), eq(false));
        when(this.configs.readPublicValues(anyList())).thenReturn(Map.of());
        doReturn(true).when(this.service).update(any(Wrapper.class));
        MiniappUserEntity result = this.service.findOrCreate("openid-a");
        assertTrue(result.getNickname().matches("用户\\d{6}"), "空昵称回填生成的默认昵称");
        assertEquals("", result.getAvatarUrl(), "未配置默认头像时保持空值");
        verify(this.service).update(any(Wrapper.class));
    }

    @Test
    void profileMarksCustomizedOnlyWhenBothFieldsProvided() {
        MiniappUserEntity user = this.user();
        doReturn(user).when(this.service).findOrCreate("openid-a");
        when(this.configs.readPublicValues(anyList()))
                .thenReturn(Map.of("miniapp.default_avatar_url", "/assets/default-avatar.png"));
        assertTrue(this.service.profile("openid-a").customized(), "用户自填头像昵称视为已提供");
        user.setAvatarUrl("/assets/default-avatar.png");
        user.setNickname("用户123456");
        assertFalse(this.service.profile("openid-a").customized(), "默认头像与生成昵称不视为用户提供");
        user.setNickname("自定义昵称");
        user.setAvatarUrl("https://cdn.example/uploaded.png");
        assertTrue(this.service.profile("openid-a").customized(), "自填昵称与上传头像视为已提供");
        user.setAvatarUrl("");
        assertFalse(this.service.profile("openid-a").customized(), "缺少头像时不视为用户提供");
    }

    @Test
    void rejectsArbitraryOrAnotherAccountsAvatarAndTrimsNickname() {
        doReturn(this.user()).when(this.service).findOrCreate("openid-a");
        assertThrows(BusinessException.class, () -> this.service.updateProfile("openid-a",
                new MiniappProfileRequest("昵称", "https://attacker.example/a.png")));
        verify(this.service, never()).update(any(Wrapper.class));
        doReturn(true).when(this.service).update(any(Wrapper.class));
        assertEquals("昵称", this.service.updateProfile("openid-a",
                new MiniappProfileRequest(" 昵称 ", "https://cdn.example/a.png")).nickname());
        assertEquals("", this.service.updateProfile("openid-a", new MiniappProfileRequest("", "")).avatarUrl());
    }

    @Test
    void avatarUploadBindsOnlyCurrentUserAndReportsWriteFailure() {
        doReturn(this.user()).when(this.service).findOrCreate("openid-a");
        MockMultipartFile file = new MockMultipartFile("file", new byte[]{1});
        when(this.assets.saveAvatarImage(file)).thenReturn(new AssetResponse("https://cdn.example/new.png"));
        doReturn(true).when(this.service).update(any(Wrapper.class));
        assertEquals("https://cdn.example/new.png", this.service.uploadAvatar("openid-a", file).avatarUrl());
        doReturn(false).when(this.service).update(any(Wrapper.class));
        assertThrows(BusinessException.class, () -> this.service.uploadAvatar("openid-a", file));
    }

    @Test
    void unchangedNicknameSkipsContentSecurityCheck() {
        doReturn(this.user()).when(this.service).findOrCreate("openid-a");
        doReturn(true).when(this.service).update(any(Wrapper.class));
        assertEquals("旧昵称", this.service.updateProfile("openid-a",
                new MiniappProfileRequest(" 旧昵称 ", "https://cdn.example/a.png")).nickname());
        verify(this.security, never()).isTextAllowed(any(), any(), anyInt());
    }

    @Test
    void riskyNicknameIsRejectedBeforeUpdate() {
        doReturn(this.user()).when(this.service).findOrCreate("openid-a");
        when(this.security.isTextAllowed("openid-a", "违规昵称", WechatContentSecurityClient.SCENE_PROFILE))
                .thenReturn(false);
        assertThrows(BusinessException.class, () -> this.service.updateProfile("openid-a",
                new MiniappProfileRequest("违规昵称", "https://cdn.example/a.png")));
        verify(this.service, never()).update(any(Wrapper.class));
    }

    private MiniappUserEntity user() {
        MiniappUserEntity user = new MiniappUserEntity();
        user.setId(10L);
        user.setOpenId("openid-a");
        user.setNickname("旧昵称");
        user.setAvatarUrl("https://cdn.example/a.png");
        return user;
    }
}
