package com.personalink.server.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.personalink.server.dto.AssetResponse;
import com.personalink.server.dto.MiniappProfileRequest;
import com.personalink.server.entity.MiniappUserEntity;
import com.personalink.server.exception.BusinessException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MiniappUserServiceImplTest {
    private LocalAssetService assets;
    private MiniappUserServiceImpl service;

    @BeforeEach
    void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "profile-test"),
                MiniappUserEntity.class);
        this.assets = mock(LocalAssetService.class);
        this.service = spy(new MiniappUserServiceImpl(this.assets));
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
    void newAccountHasOptionalEmptyProfile() {
        doReturn(null).when(this.service).getOne(any(Wrapper.class), eq(false));
        doReturn(true).when(this.service).save(any(MiniappUserEntity.class));
        MiniappUserEntity created = this.service.findOrCreate("openid-a");
        assertEquals("openid-a", created.getOpenId());
        assertEquals("", created.getNickname());
        assertEquals("", created.getAvatarUrl());
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

    private MiniappUserEntity user() {
        MiniappUserEntity user = new MiniappUserEntity();
        user.setId(10L);
        user.setOpenId("openid-a");
        user.setNickname("旧昵称");
        user.setAvatarUrl("https://cdn.example/a.png");
        return user;
    }
}
